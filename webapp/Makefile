cp = /usr/share/java/tomcat-servlet-api.jar:build/lib/apache-commons-lang.jar:build/lib/mongo.jar
webappsdir = /var/lib/tomcat/webapps
name = bigearth

all: get-libs
	mkdir -p build/classes
	javac -cp $(cp) -d build/classes `find src -name '*.java'`

get-libs:
	mkdir -p build/lib
	cp /usr/share/java/apache-commons-lang.jar build/lib
	cp /usr/share/java/mongo.jar build/lib

dist:
	-rm -rf dist_dir
	mkdir dist_dir
	cp -rv WEB-INF dist_dir
	cp -rv build/* dist_dir/WEB-INF
	cp -rv web/* dist_dir
	cd dist_dir; jar -cf ../$(name).war *
	rm -rf dist_dir

install: dist
	-rm -rf $(webappsdir)/$(name)
	cp $(name).war $(webappsdir)
