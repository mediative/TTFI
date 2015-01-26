scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
	"org.specs2" %% "specs2-core" % "2.4.15" % "test"
)

scalacOptions in Test += "-Yrangepos"

scalacOptions ++= Seq(
  "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    // "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture"
)