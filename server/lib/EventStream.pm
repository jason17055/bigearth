package EventStream;
use strict;
use warnings;
use JSON;

my %queued_events_by_sid;
my %waiting_event_listeners_by_sid;

sub new
{
	my $class = shift;
	return bless {
		waiting_listeners => [],
		queued_events => [],
		}, $class;
}

sub handle_event_request
{
	my $self = shift;
	my ($req, $http) = @_;

	if ($self->{queued_events} && @{$self->{queued_events}})
	{
		my $evt = shift @{$self->{queued_events}};
		send_event($evt, $http);
		return;
	}
	else
	{
		# must wait
		$self->{waiting_listeners} ||= [];
		push @{$self->{waiting_listeners}}, $http;
	}
	return;
}

sub post_event
{
	my $self = shift;
	my ($evt) = @_;

	if ($self->{waiting_listeners} && @{$self->{waiting_listeners}})
	{
		my $http = shift @{$self->{waiting_listeners}};
		send_event($evt, $http);
	}
	else
	{
		$self->{queued_events} ||= [];
		push @{$self->{queued_events}}, $evt;
	}
	return $evt;
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

1;
