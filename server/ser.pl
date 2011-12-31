#!/usr/bin/perl -Ilib

use strict;
use warnings;
use Getopt::Long;
use URI::Escape;

use MainLoop;
use Sys::Syslog;
use HTTP::Response;
use Time::HiRes "time";
use JSON "encode_json", "decode_json";

my $http_port = 2626;
my $map_name = "nippon";
my $master_url = 'http://jason.long.name/trains';
GetOptions(
	"port=i" => \$http_port,
	"map=s" => \$map_name,
	) or exit 2;

my $main = MainLoop->new();
openlog "trains-server", "cons,pid,perror", "user";
setup_listener();

use TrainsGame;
our $server_start_time = time();
my $gamestate = TrainsGame->new();
$gamestate->load_map($map_name);

use EventStream;
$gamestate->{events} = EventStream->new();

post_gametable_advertisement();

eval { $main->run };
my $E = $@;
die $E if $E && $E !~ /^got (TERM|INT) signal/;

update_gametable_advertisement("D");

my $http_socket;
my %http_connections;

sub setup_listener
{
	use IO::Socket::INET;
	{
		my $sd = IO::Socket::INET->new(
			Proto => "tcp",
			Listen => 5,
			LocalPort => $http_port,
		)
		or die;
		MainLoop->add_socket($sd,
			on_readable => sub { accept_http_connection($sd) },
			name => "http_listener",
		);
		$http_socket = $sd;

		if (!$http_port)
		{
			$http_port = $sd->sockport;
		}
	}
}

sub accept_http_connection
{
	my ($listener_sd) = @_;

	my $sd = $listener_sd->accept
		or die "Error: accept: $!\n";

	use JDBS_HttpStream;
	my $http = JDBS_HttpStream->new($sd);
	$http->callback(
		on_received => \&handle_http_request,
		on_close => \&handle_http_close,
		);
	$http_connections{$http->{name}} = $http;
	syslog "info", "got http connection from %s", $http->{name};
	return;
}

sub handle_http_close
{
	my ($http) = @_;

	# Note: we do not need to MainLoop->remove_socket() here
	# because HTTP stream does that for us before calling the
	# on_close() callback (i.e. this function).

	syslog "notice", "http connection %s closed", $http->{name};
	delete $http_connections{$http->{name}};
}

sub handle_http_request
{
	my ($req, $http) = @_;

	my $method = $req->method;
	my $path = $req->uri;

	# look for and read the "sid" cookie in the request
	my $cookies = $req->header("Cookie") || "";
	my $sid = (grep $_, map { /sid=(.*)/ and $1 } split /\s*;\s*/, $cookies)[0] || "-";

	local $ENV{SESSION_ID} = $sid;
	local $ENV{REMOTE_ADDR} = $http->{tcp_sd}->peerhost;
	local $ENV{REMOTE_USER};

	syslog "info", "%s %s %s %s %s",
			$ENV{REMOTE_ADDR} || "-",
			$ENV{SESSION_ID} || "-",
			$ENV{REMOTE_USER} || "-",
			$method,
			$path;

	if ($path eq "/")
	{
		$path = "/index.html";
	}

	if ($path =~ m{^/event\b})
	{
		$gamestate->{events}->handle_event_request($req, $http);
		return;
	}
	elsif ($path =~ m{^/([\w\d-]+)})
	{
		my $meth = "handle_$1_request";
		if ($gamestate->can($meth))
		{
			my $resp = $gamestate->$meth($req);
			if (!$resp)
			{
				$resp = HTTP::Response->new("500");
				$resp->content("Internal server error\n");
			}
			$http->write_message($resp);
			return;
		}
	}

	my $resp;
	if (open my $fh, "<", "../html$path")
	{
		$resp = HTTP::Response->new("200", "OK");
		$resp->header("Content-Type",
			($path =~ /\.html$/) ? "text/html" :
			($path =~ /\.png$/) ? "image/png" :
			($path =~ /\.js$/) ? "text/javascript" :
			"text/plain");
		local $/;
		binmode $fh;
		my $d = <$fh>;
		$resp->content($d);
		close $fh
			or die "../html$path: $!\n";
	}

	if (!$resp)
	{
		$resp = HTTP::Response->new("404", "Not Found");
		$resp->header("Content-Type", "text/html");
		$resp->content("<p>Not found</p>\n");
	}
	$http->write_message($resp);
}

my $gametable_id;
sub post_gametable_advertisement
{
	use LWP::UserAgent;
	use HTTP::Request::Common "POST";
	use Sys::Hostname;
	use IO::Handle;

	print "posting gametable advertisement...";
	STDOUT->flush;

	my $host = hostname();
	my $my_url = "http://$host:$http_port";

	my $ua = LWP::UserAgent->new;
	my $url = "$master_url/server-api/new_gametable.php";
	my $resp = $ua->request(POST $url, [ map => $map_name, url => $my_url ]);
	if ($resp->is_success)
	{
		my @lines = split /\n/, $resp->content;
		if ($lines[0] eq "ok")
		{
			$lines[1] =~ /id=(\d+)/
			and $gametable_id = $1;

			print "ok, id is $gametable_id\n";
			return;
		}
	}
	warn "unable to post gametable\n";
}

sub update_gametable_advertisement
{
	my ($status) = @_;

	use LWP::UserAgent;
	use HTTP::Request::Common "POST";
	use IO::Handle;

	print "updating gametable advertisement...";
	STDOUT->flush;

	my $ua = LWP::UserAgent->new;
	my $url = "$master_url/server-api/gametable.php?id=" . uri_escape($gametable_id);
	$ua->request(POST $url, [ status => $status ]);

	print "done\n";
}
