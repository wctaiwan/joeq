PACKAGES = Allocator Assembler Bootstrap ClassLib Clazz Compil3r Debugger GC Interpreter Linker Main Memory Run_Time Scheduler Support Synchronization UTF Util
SUPPORT_LIBS = Support/javabdd_0.5.jar
JDK14SUPPORT_LIB = Support/java14.jar

ifeq (${OS},Windows_NT)
CLASSPATH_SEP = \;
BUILD_CLASSPATH = ".;$(subst $(space),;,$(SUPPORT_LIBS))"
JOEQ_DIR = $(shell cygpath -ma .)
else
CLASSPATH_SEP = :
BUILD_CLASSPATH = .:$(subst $(space),:,$(SUPPORT_LIBS))
JOEQ_DIR = $(PWD)
endif

ALLDIRS := $(shell find $(PACKAGES) -type d | grep -v CVS)
ALLJAVA := $(foreach d, $(ALLDIRS), $(wildcard $(d)/*.java))
ALLPKGS := $(sort $(foreach j, $(ALLJAVA), $(patsubst %/,%,$(dir $(j)))))
ALLPKGNAMES := $(subst /,.,$(ALLPKGS))
ALLCLASS := $(foreach d, $(ALLDIRS), $(wildcard $(d)/*.class))

CLASSFILE_VERSION = 1.3

all:	javac

### RULES TO BUILD SOURCE CODE
#
jikes:	bootclasspath source_list _jikes
# _jikes: Assumes prior existence of the .source_list file.
# Useful on cygwin, because cygwin takes a long time to build the .source_list file.
_jikes:	
	jikes -target $(CLASSFILE_VERSION) -bootclasspath @bootclasspath -classpath $(BUILD_CLASSPATH) @.source_list

jikes-pedantic:	bootclasspath source_list
	jikes -target $(CLASSFILE_VERSION) -bootclasspath @bootclasspath -classpath $(BUILD_CLASSPATH) +P @.source_list

javac:	bootclasspath source_list _javac
# _javac: Assumes prior existence of the .source_list file.
# Useful on cygwin, because cygwin takes a long time to build the .source_list file.
_javac:	
#	javac -target $(CLASSFILE_VERSION) -bootclasspath @bootclasspath -classpath $(BUILD_CLASSPATH) @.source_list
	javac -target $(CLASSFILE_VERSION) -bootclasspath "`cat bootclasspath`" -classpath $(BUILD_CLASSPATH) @.source_list


### RULES TO CLEAN
#
clean:
	find . -name "*.class" | xargs rm -f
#	rm -f $(ALLCLASS)

veryclean:
	find . -name "*.class" | xargs rm -f bootclasspath .source_list
#	rm -f bootclasspath .source_list $(ALLCLASS)


### RULES TO ACCESS CVS
#
update:
	( export CVS_RSH=ssh ; cvs update -Pd )

commit:
	( export CVS_RSH=ssh ; cvs commit )


### RULES FOR BUILDING BOOT IMAGES
#
bootstrap:	Main/Bootstrapper.class
	java -Xbootclasspath/a:$(JDK14SUPPORT_LIB) -cp .$(CLASSPATH_SEP)$(JOEQ_DIR)$(CLASSPATH_SEP)../joeq -mx450M Main.Bootstrapper

bootstrap-sun142-linux:	javac
	java -cp .:$(JOEQ_DIR):../joeq -ms260M -mx260M Main.Bootstrapper -cl ClassLib/sun142_linux/classlist.txt

bootstrap-sun14-linux:	javac
	java -cp .:$(JOEQ_DIR):../joeq -ms240M -mx240M Main.Bootstrapper -cl ClassLib/sun14_linux/classlist.txt

bootstrap-sun13-linux:	javac
	java -Xbootclasspath/a:$(JDK14SUPPORT_LIB) -cp .:$(JOEQ_DIR):../joeq -ms200M -mx200M Main.Bootstrapper -cl ClassLib/sun13_linux/classlist.txt

bootstrap-ibm13-linux:	javac
	java -Xbootclasspath/a:$(JDK14SUPPORT_LIB):/opt/IBMJava2-131/jre/lib/ext/ibmjcaprovider.jar -cp .:$(JOEQ_DIR):../joeq -ms230M -mx230M Main.Bootstrapper -cl ClassLib/ibm13_linux/classlist.txt

bootstrap-sun142-win32:	javac
	java -cp .\;$(JOEQ_DIR)\;../joeq -ms185M -mx185M Main.Bootstrapper -cl ClassLib/sun142_win32/classlist.txt

bootstrap-sun14-win32:	javac
	java -cp .\;$(JOEQ_DIR)\;../joeq -ms180M -mx180M Main.Bootstrapper -cl ClassLib/sun14_win32/classlist.txt

bootstrap-sun13-win32:	javac
	java -cp .\;$(JOEQ_DIR)\;../joeq -Xbootclasspath/a:$(JDK14SUPPORT_LIB) -ms120M -mx120M Main.Bootstrapper -cl ClassLib/sun13_win32/classlist.txt

bootstrap-ibm13-win32:	javac
	java -cp .\;$(JOEQ_DIR)\;../joeq -Xbootclasspath/a:$(JDK14SUPPORT_LIB)\;C:\\Program\ Files\\IBM\\Java13\\jre\\lib\\ext\\IBMJCEProvider.jar -ms150M -mx150M Main.Bootstrapper -cl ClassLib/ibm13_win32/classlist.txt

### RULES FOR BUILDING JAVADOC
#
javadoc:
	javadoc -breakiterator -d ../joeq_project/htdocs/javadoc $(ALLPKGNAMES)


### MISC RULES
#
wc:
	@echo Total, and Top Five files:
	@wc -l $(ALLJAVA) | sort -rn | head -7


### LOCAL RULES (you will never need to make these directly)
#
source_list:
	@echo $(ALLJAVA) | xargs -n 1 echo > .source_list
#	find $(PACKAGES) -name "*.java" > .source_list

Main/Bootstrapper.class:
	$(MAKE) javac

bootclasspath:	Main/GetBootClassPath.class
	java Main.GetBootClassPath $(JDK14SUPPORT_LIB) > bootclasspath

Main/GetBootClassPath.class:	Main/GetBootClassPath.java
	javac Main/GetBootClassPath.java

empty:=
space:=$(empty) $(empty)
