name := "akkaanalytics"

version := "0.1"

scalaVersion := "2.10.3"

resolvers ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= {
  val akkaVersion  = "2.3.0"
  Seq(
    /** Akka **/
    "com.typesafe.akka"      %% "akka-actor"  % akkaVersion,
    /** JSON **/
    "io.spray"               %% "spray-json"  % "1.3.1",
    /** Date-times **/
    "com.github.nscala-time" %% "nscala-time" % "2.0.0",
  )
}
