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
my $server_start_time = time();
my $gamestate = TrainsGame->new();
$gamestate->load_map($map_name);

post_gametable_advertisement();
my %queued_events_by_sid;
my %waiting_event_listeners_by_sid;

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

	if ($path eq "/gamestate")
	{
		my $resp = handle_gamestate_request($req);
		$http->write_message($resp);
		return;
	}
	elsif ($path =~ m{^/event\b})
	{
		handle_event_request($req, $http);
		return;
	}
	elsif ($path =~ m{^/join\b})
	{
		my $resp = handle_join_request($req);
		$http->write_message($resp);
		return;
	}
	elsif ($path =~ m{^/request/(.*)$}s)
	{
		my $verb = $1;
		my $resp = handle_a_request($req, $verb);
		$http->write_message($resp);
		return;
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

sub handle_join_request
{
	my ($req) = @_;

	my $path = $req->uri;
	my $sid = uri_unescape($path =~ /sid=([^&;]*)$/ and $1);
	
	my $resp = HTTP::Response->new("303");
	$resp->header("Set-Cookie", "sid=$sid");
	$resp->header("Location", "/");
	return $resp;
}

sub handle_gamestate_request
{
	my ($req) = @_;

	my $resp = HTTP::Response->new("200", "OK");
	$resp->header("Content-Type", "text/json");
	my $stat_struct = get_gamestate();
	my $content = encode_json($stat_struct);
	$resp->content($content);
	return $resp;
}

sub get_gamestate
{
	return {
	serverTime => int((time - $server_start_time) * 1000),
	startTime => 120000,
	rails => $gamestate->{rails},
	map => $gamestate->{map},
	};
}

sub my_unescape
{
	my $x = shift;
	$x =~ s/\+/ /gs;
	return uri_unescape($x);
}

sub handle_a_request
{
	my ($req, $verb) = @_;

	my @d = split /&/, $req->content;
	my %data = map { my ($k,$v) = split /=/, $_; my_unescape($k) => my_unescape($v) } @d;

#use Data::Dumper;
#print STDERR Dumper(\%data);

	if ($verb eq "build")
	{
		handle_build_request(\%data);
	}
	elsif ($verb eq "editMap")
	{
		handle_editMap_request(\@d);
	}
	else
	{
		syslog "warning", "verb %s not found", $verb;
		my $resp = HTTP::Response->new("404", "Not found");
		$resp->content("");
		return $resp;
	}

	my $resp = HTTP::Response->new("200", "OK");
	$resp->header("Content-Type", "text/json");
	my $content = encode_json({});
	$resp->content($content);
	return $resp;
}

sub handle_build_request
{
	my ($args) = @_;

	foreach my $track_idx (split /\s+/, $args->{rails})
	{
		$gamestate->{rails}->{$track_idx} = 1;
	}
	fire_event($ENV{SESSION_ID},
		{ event => "track-built" });

	return;
}

sub handle_editMap_request
{
	my ($args_arrayref) = @_;

	my $map = {
		cities => {},
		};
	foreach my $vv (@$args_arrayref)
	{
		my ($k, $v) = split /=/, $vv, 2;
		$k = my_unescape($k);
		$v = my_unescape($v);

		if ($k eq "terrain")
		{
			my @terrain = split /\n/, $v;
			$map->{terrain} = \@terrain;
		}
		elsif ($k eq "rivers")
		{
			my %tmp = map { $_ => 1 } split / /, $v;
			$map->{rivers} = \%tmp;
		}
		elsif ($k =~ m{^cities\[(\d+)\]\[name\]$})
		{
			$map->{cities}->{$1} ||= {};
			$map->{cities}->{$1}->{name} = $v;
		}
		elsif ($k =~ m{^cities\[(\d+)\]\[offers\]\[\]$})
		{
			$map->{cities}->{$1} ||= {};
			$map->{cities}->{$1}->{offers} ||= [];
			push @{$map->{cities}->{$1}->{offers}}, $v;
		}
		else
		{
			print STDERR "what is $k\n";
		}
	}

	open my $fh, ">", "map.txt";
	print $fh encode_json($map);
	close $fh;

	return;
}

sub handle_event_request
{
	my ($req, $http) = @_;

	my $sid = $ENV{SESSION_ID};
	if ($queued_events_by_sid{$sid}
		&& @{$queued_events_by_sid{$sid}})
	{
		my $evt = shift @{$queued_events_by_sid{$sid}};
		send_event($evt, $http);
		return;
	}
	else
	{
		# must wait
		$waiting_event_listeners_by_sid{$sid} ||= [];
		push @{$waiting_event_listeners_by_sid{$sid}}, $http;
	}
}

sub fire_event
{
	my ($sid, $evt) = @_;

	if ($waiting_event_listeners_by_sid{$sid}
		&& @{$waiting_event_listeners_by_sid{$sid}})
	{
		my $http = shift @{$waiting_event_listeners_by_sid{$sid}};
		send_event($evt, $http);
	}
	else
	{
		$queued_events_by_sid{$sid} ||= [];
		push @{$queued_events_by_sid{$sid}}, $evt;
	}
}

sub send_event
{
	my ($evt, $http) = @_;

	my $resp = HTTP::Response->new("200","OK");
	$resp->header("Content-Type", "text/json");
	my $content = encode_json($evt);
	$resp->content($content);
	$http->write_message($resp);
	return;
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