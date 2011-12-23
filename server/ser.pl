#!/usr/bin/perl -Ilib

use strict;
use warnings;
use Getopt::Long;
use URI::Escape;

use MainLoop;
use Sys::Syslog;
use HTTP::Response;
use Time::HiRes "time";
use JSON "encode_json";

my $http_port = 2626;
GetOptions(
	"port=i" => \$http_port,
	) or exit 2;

my $main = MainLoop->new();
openlog "trains-server", "cons,pid,perror", "user";
setup_listener();

my $server_start_time = time();
my $gamestate = {
	rails => {},
	};

eval { $main->run };
my $E = $@;
die $E if $E && $E !~ /^got (TERM|INT) signal/;

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

	# TODO- set this global variable to the session identifier,
	# i.e. something unique that represents this connection
	local $ENV{SESSION_ID} = "dummysid";
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
	elsif ($path eq "/event")
	{
		handle_event_request($req, $http);
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

	return;
}

my @events;
my @pending_event_listeners;
sub handle_event_request
{
	my ($req, $http) = @_;

	if (my $evt = shift @events)
	{
		send_event($evt, $http);
		return;
	}
	else
	{
		# must wait
		print STDERR "waiting for event to occur\n";
		MainLoop->add_timer(5,
			on_timeout => sub {
				fire_event({x=>"1"});
			});
		MainLoop->add_timer(10,
			on_timeout => sub {
				fire_event({x=>"2"});
			});
		push @pending_event_listeners, $http;
	}
}

sub fire_event
{
	my ($evt) = @_;

	if (@pending_event_listeners)
	{
		my $http = shift @pending_event_listeners;
		send_event($evt, $http);
	}
	else
	{
		push @events, $evt;
	}
}

sub send_event
{
	my ($evt, $http) = @_;

	my $resp = HTTP::Response->new("200","OK");
	$resp->header("Content-Type", "text/json");
	my $content = encode_json({
		event => $evt,
		});
	$resp->content($content);
	$http->write_message($resp);
	return;
}
