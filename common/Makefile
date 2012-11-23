classpath = "../jlib/vecmath.jar;../jlib/jackson-core-2.1.0.jar;../jlib/jetty.jar;../jlib/servlet.jar;../jlib/jetty-util.jar"

all:
	mkdir -p classes
	javac -d classes -cp $(classpath) -Xlint:unchecked -Xlint:deprecation `find . -name '*.java'`
