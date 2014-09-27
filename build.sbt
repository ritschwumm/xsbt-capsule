sbtPlugin		:= true

name			:= "xsbt-capsule"

organization	:= "de.djini"

version			:= "0.2.0"

addSbtPlugin("de.djini" % "xsbt-classpath" % "0.10.0")
	
scalacOptions	++= Seq(
	"-deprecation",
	"-unchecked",
	"-language:implicitConversions",
	// "-language:existentials",
	// "-language:higherKinds",
	// "-language:reflectiveCalls",
	// "-language:dynamics",
	// "-language:postfixOps",
	// "-language:experimental.macros"
	"-feature"
)
