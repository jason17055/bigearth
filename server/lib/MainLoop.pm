use strict;
use warnings;

package MainLoop;
use IO::Select;
use Time::HiRes "time";

our $selector_read = IO::Select->new;
our $selector_write = IO::Select->new;
our $selector_except = IO::Select->new;
our $selector_all = IO::Select->new;
our @timeouts = ();
our $current_loop;

sub new
{
	my $class = shift;
	my $self = bless {
		}, $class;
	return $self;
}

=head2 add_socket() - watch for socket activity on the main loop

  $main->add_socket( $socket_fd,
          on_readable => \&readable_func,
          on_writable => \&writable_func,
          on_exception => \&exception_func,
          );

=cut

sub add_socket
{
	my $self = shift;
	my ($sd, %args) = @_;

	my $handle = [ $sd, \%args ];

	$selector_all->add($handle);
	if ($args{on_readable})
	{
		$selector_read->add($handle);
	}
	if ($args{on_writable})
	{
		$selector_write->add($handle);
	}
	if ($args{on_exception})
	{
		$selector_except->add($handle);
	}
}

=head2 add_timer() - add a timeout event to the main loop

  $main->add_timer( 5,
          on_timeout => \&timeout_func,
          );

Returns a timer handle, which can be used in remove_timer().

=cut

sub add_timer
{
	my $self = shift;
	my $timeout = shift;
	return $self->add_timer_absolute(time() + $timeout, @_);
}

# add a timer by specifying an absolute time, instead of an interval from now
#
sub add_timer_absolute
{
	my $self = shift;
	my ($timeout_abs, %args) = @_;

	my $timeout_struct = [ $timeout_abs, \%args ];
	@timeouts = sort { $a->[0] <=> $b->[0] }
		@timeouts,
		$timeout_struct;
	return $timeout_struct;
}

=head2 remove_timer()

=cut

sub remove_timer
{
	my $self = shift;
	my ($timeout_struct) = @_;

	@timeouts = grep { $_ != $timeout_struct } @timeouts;
	return;
}

sub debug_selector
{
	my $selector = shift;

	my @handles = $selector->handles;
	for (my $i = 0; $i < @handles; $i++)
	{
		print STDERR "  $i: $handles[$i]->[1]->{name}\n";
	}
}

=head2 exit()

  $main->exit();

Causes the run() method to stop blocking, so the program can exit.

=cut

sub exit
{
	undef $current_loop;
}

sub modify_socket_read_sensitivity
{
	my $self = shift;
	my ($sd, $read_sensitive) = @_;

	my $handle = $selector_all->exists($sd)
		or return;
	if ($read_sensitive)
	{
		$selector_read->add($handle);
	}
	else
	{
		$selector_read->remove($handle);
	}
}

sub modify_socket_write_sensitivity
{
	my $self = shift;
	my ($sd, $write_sensitive) = @_;

	my $handle = $selector_all->exists($sd)
		or return;
	if ($write_sensitive)
	{
		$selector_write->add($handle);
	}
	else
	{
		$selector_write->remove($handle);
	}
}

sub remove_socket
{
	my $self = shift;
	my ($sd) = @_;

	$selector_all->remove($sd);
	$selector_read->remove($sd);
	$selector_write->remove($sd);
	$selector_except->remove($sd);
}

sub run
{
	my $self = shift;

	$current_loop = $self;
	local $SIG{TERM} = sub {
		undef $current_loop;
		die "got TERM signal";
		};
	local $SIG{INT} = sub {
		undef $current_loop;
		die "got INT signal";
		};

	while ($current_loop && $current_loop == $self)
	{
		my $timeout = undef;
		if (my $next_timer = $timeouts[0])
		{
			$timeout = $next_timer->[0] - time();
			if ($timeout <= 0)
			{
				shift @timeouts;
				$next_timer->[1]->{on_timeout}->();
				next;
			}
		}

	#	warn "[$$] select: selecting on $count sockets\n";
	#	debug_selector($selector_read);

		$! = 0;
		my ($ready_read, $ready_write, $ready_except)
			= IO::Select::select($selector_read,
					$selector_write, undef, $timeout)
			or ($! ? die("[$$] select: $!\n") : next);

		foreach my $fh (@$ready_except)
		{
			$fh->[1]->{on_exception}->($fh->[0]);
		}
		foreach my $fh (@$ready_write)
		{
			$fh->[1]->{on_writable}->($fh->[0]);
		}
		foreach my $fh (@$ready_read)
		{
			$fh->[1]->{on_readable}->($fh->[0]);
		}
	}
}

1;
