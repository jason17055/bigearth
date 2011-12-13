#!/usr/bin/perl -Ilib

use strict;
use warnings;
use Getopt::Long;

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
setup_listener();

my $server_start_time = time();

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

	my $path = $req->uri;
	my $resp;

print "path=$path\n";
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
	startTime => 120000
	};
}
