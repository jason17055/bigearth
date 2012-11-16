#!/bin/sh

classpath_separator=";"
classpath=$(
	echo -n classes
	find ../jlib -name '*.jar' |{ while read f; do
		echo -n "$classpath_separator$f"
	done; }
	)

java -cp "$classpath" -enableassertions -Xshare:off bigearth.WorldViewer "$@"
