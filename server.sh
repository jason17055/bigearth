#!/bin/sh

worldname=world1

ls world_backups | grep "^$worldname\\." | sort | head --lines=-2 |xargs -r -I X -- rm -rvf world_backups/X

n=`date '+%Y%m%d.%H%M%S'`
backupname="world_backups/$worldname.$n.tar.gz"
echo "backing up $worldname to \`$backupname'"
tar -czf $backupname $worldname

classpath_separator=";"
classpath=$(
	echo -n target/build
	find extlib -name '*.jar' |{ while read f; do
		echo -n "$classpath_separator$f"
	done; }
	)

java -cp "$classpath" -enableassertions -Xshare:off bigearth.BigEarthServer "$@"
