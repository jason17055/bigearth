use strict;
use warnings;

package JDBS_HttpStream;
use IO::Socket::INET;
use Time::HiRes "time";
use HTTP::Request;

=head1 NAME

JDBS_HttpStream - represents a TCP connection for HTTP messages

=head1 DESCRIPTION

Must be used with L<MainLoop>.

=head1 PROPERTIES

	# the $http object holds properties of a single http stream
	# it has the following properties:
	#   tcp_sd  - the socket descriptor
	#   buf     - bytes that have been read but not yet processed
	#   msg_out - bytes that are ready to be written (not yet sent)
	#   duplex  - if true, this http stream is full-duplex,
	#              i.e. requests are sent both ways, and responses
	#              are asynchronous
	#   callbacks - http-related callback subroutines
	#   current_request - the $req object while the request is being
	#              received
	#   content - body of request as it is read in
	#   content_length - number of bytes to read as the body of the msg
	#   numread - used for rate-limiting incoming traffic
	#   numread_since - used for rate-limiting incoming traffic
	#   input_rate_limit - sets the limit of incoming traffic, in bytes/sec

=head1 CONSTRUCTOR

=head2 new() - configure a socket for sending/receiving HTTP messages

  my $http = JDBS_HttpStream->new($tcp_sd);
  $http->callback(
              on_received => \&on_received_http_request,
              on_close => \&on_socket_closed,
            );

=cut

sub new
{
	my $class = shift;
	my ($sd) = @_;

	my $self = bless {
		tcp_sd => $sd,
		buf => "",
		callbacks => {},
		numread => 0,
		numread_since => time,
		input_rate_limit => 4000,
		created => time,
		numread_total => 0,
		numsent_total => 0,
		num_messages_received => 0,
		num_messages_sent => 0,
		}, $class;

	$self->{name} = $sd->peerhost . ":" . $sd->peerport;
	MainLoop->add_socket($sd,
		on_readable => sub { $self->on_socket_readable },
		on_writable => sub { $self->on_socket_writable },
		name => "tcp_socket_$self->{name}",
		);

	$sd->blocking(0);
	return $self;
}

=head1 METHODS

=head2 callback() - configure one or more callbacks

Recognized callbacks are:

=over

=item on_received

This callback is invoked whenever an incoming HTTP message has been
completely received. Its arguments are: the $req received, and the
$http object itself.

=item on_idle

This callback is invoked whenever the stream has nothing to send.
Its arguments are: the $http object itself.

=item on_close

This callback is invoked when the stream has been closed, from an
error occurring or the remote host closing the connection. If it
is closed locally, from the program itself, the callback is not
invoked. Its arguments are: the $http object itself.

=back

=cut

sub callback
{
	my $self = shift;
	while (@_)
	{
		my $callback_name = shift;
		my $callback_subref = shift;
		$self->{callbacks}->{$callback_name} = $callback_subref;
	}
	return;
}

sub handle_tcp_command_content
{
	my $conninfo = shift;
	my ($bytes) = @_;

	$conninfo->{content} .= $bytes;
	if ($conninfo->{content_length} == 0)
	{
		$conninfo->{current_request}->content($conninfo->{content});
		$conninfo->message_received($conninfo->{current_request});
		$conninfo->{current_request} = undef;
		$conninfo->{content} = undef;
	}
}

sub handle_tcp_command_header
{
	my $conninfo = shift;
	my ($command_header) = @_;

	$command_header =~ s/^(\015?\012)+//;
	$command_header =~ s/(\015?\012)+$//;
	my @lines = split /\015?\012/s, $command_header;
	my $header = shift @lines;

	my ($method, $path, $prot) = split /\s+/, $header, 3;
	my %headers = ();
	foreach my $l (@lines)
	{
		my ($n, $v) = split /:\s*/, $l, 2;
		$headers{lc $n} = $v;
	}

	my $req = HTTP::Request->new($method, $path, [ %headers ], "");

	if (my $len = $headers{"content-length"})
	{
		$conninfo->{current_request} = $req;
		$conninfo->{content_length} = $len;
		$conninfo->{content} = "";
		return;
	}
	else
	{
		$conninfo->message_received($req);
	}
}

sub message_received
{
	my $conninfo = shift;
	my ($req) = @_;

	$conninfo->{num_messages_received}++;
	$conninfo->{callbacks}->{on_received}->($req, $conninfo);
}

sub http_process_input_buffer
{
	my $conninfo = shift;

	# process commands
	while (length $conninfo->{buf})
	{
		my $is_outbound_msg = defined($conninfo->{msg_out})
				&& length($conninfo->{msg_out});
		$is_outbound_msg ||= ($conninfo->{num_messages_received} > $conninfo->{num_messages_sent});
		if ($is_outbound_msg && !$conninfo->{duplex})
		{
			# there's an outbound message, and this is not a full-duplex
			# socket, so stop processing the input buffer and wait
			# for the outbound buffer to be flushed
			return;
		}

		if ($conninfo->{content_length})
		{
			my $nbytes = length($conninfo->{buf});
			$nbytes = $conninfo->{content_length}
				if ($conninfo->{content_length} < $nbytes);

			my $bytes = substr($conninfo->{buf}, 0, $nbytes);
			$conninfo->{buf} = substr($conninfo->{buf}, $nbytes);
			$conninfo->{content_length} -= $nbytes;

			$conninfo->handle_tcp_command_content($bytes);
		}
		elsif ($conninfo->{buf} =~ s/^(.*?\015?\012\015?\012)//s)
		{
			my $command_header = $1;
			$conninfo->handle_tcp_command_header($command_header);
		}
		else
		{
			last;
		}
	}

	# if you're here, that means we need to wait for more bytes
	# to come in before more requests can be processed

	# determine whether we're reading faster than we should
	my $cur_time = time;
	my $required_wait_time = defined($conninfo->{input_rate_limit}) ?
		$conninfo->{numread} / $conninfo->{input_rate_limit} : 0;
	my $next_read = $conninfo->{numread_since} + $required_wait_time;
	if ($cur_time >= $next_read)
	{
		# ok to read immediately
		$conninfo->{numread} = 0;
		$conninfo->{numread_since} = $cur_time;
		MainLoop->modify_socket_read_sensitivity($conninfo->{tcp_sd}, 1);
	}
	else
	{
		# no reading until $next_read time is here
		MainLoop->modify_socket_read_sensitivity($conninfo->{tcp_sd}, 0);
		MainLoop->add_timer($next_read - $cur_time,
			on_timeout => sub {
				MainLoop->modify_socket_read_sensitivity($conninfo->{tcp_sd}, 1)
				});
	}
}

sub get_socket_name
{
	my ($sd) = @_;

	my $host = $sd->peerhost;
	my $port = $sd->peerport;
	if (defined($host) && defined($port))
	{
		return "$host:$port";
	}
	else
	{
		return "#error#";
	}
}

# on_socket_readable() - called when the socket becomes "readable",
#     i.e. there is data waiting to be read
#
sub on_socket_readable
{
	my $conninfo = shift;

	my $tcp_sd = $conninfo->{tcp_sd};

	my $data;
	my $numread = $tcp_sd->sysread($data, 2500);
	if (not defined $numread)
	{
		my $name = get_socket_name($tcp_sd);

		MainLoop->remove_socket($tcp_sd);
		close $tcp_sd;
		if ($conninfo->{callbacks}->{on_close})
		{
			$conninfo->{callbacks}->{on_close}->($conninfo);
		}
		return;
	}
	elsif ($numread == 0)
	{
		MainLoop->remove_socket($tcp_sd);
		close $tcp_sd;
		if ($conninfo->{callbacks}->{on_close})
		{
			$conninfo->{callbacks}->{on_close}->($conninfo);
		}
		return;
	}

	$conninfo->{numread} += $numread;
	$conninfo->{numread_total} += $numread;
	$conninfo->{buf} .= $data;
	$conninfo->http_process_input_buffer;
}

# on_socket_writable() - called when the socket becomes "writable",
#     i.e. there is room in the outgoing buffer for data
#
sub on_socket_writable
{
	my $conninfo = shift;

	my $tcp_sd = $conninfo->{tcp_sd};
	if (!$conninfo->{msg_out})
	{
		if ($conninfo->{callbacks}->{on_idle})
		{
			$conninfo->{callbacks}->{on_idle}->($conninfo);
		}
	}

	if (!$conninfo->{msg_out})
	{
		# nothing ready to send on this socket, so
		# no longer "select" on write events for this socket
		MainLoop->modify_socket_write_sensitivity($tcp_sd, 0);
		$conninfo->http_process_input_buffer;
		return;
	}

	my $nbytes = length($conninfo->{msg_out});
	my $nsent = $tcp_sd->syswrite($conninfo->{msg_out}, $nbytes);
	if (not defined $nsent)
	{
		# a write error has occured
		#my $name = $tcp_sd->peerhost . ":" . $tcp_sd->peerport;

		MainLoop->remove_socket($tcp_sd);
		close $tcp_sd;
		if ($conninfo->{callbacks}->{on_close})
		{
			$conninfo->{callbacks}->{on_close}->($conninfo);
		}
		return;
	}

	$conninfo->{numsent_total} += $nsent;
	if ($nsent < $nbytes)
	{
		$conninfo->{msg_out} = substr($conninfo->{msg_out}, $nsent);
	}
	else
	{
		$conninfo->{msg_out} = undef;
	}
}

=head2 write_message() - send an HTTP request/response

  $http->write_message($req);

This method places the HTTP request or HTTP response into the
outbound queue for this connection, and returns immediately
(not waiting for a response).

=cut

sub write_message
{
	my $self = shift;
	my ($msg) = @_;

	$self->{num_messages_sent}++;

	if (!$msg->protocol)
	{
		$msg->protocol("HTTP/1.0");
	}
	$msg->header("Content-Length", length($msg->content));
	$msg->header("Connection", "Keep-Alive");

	my $raw = $msg->as_string("\015\012");

	$self->{msg_out} = "" if not defined $self->{msg_out};
	$self->{msg_out} .= $raw;

	# make sure the callback responsible for sending this message
	# will get called
	MainLoop->modify_socket_write_sensitivity($self->{tcp_sd}, 1);
	return;
}

1;
