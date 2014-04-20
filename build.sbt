name := "Kademlia"

version := "0.1.1"

scalaVersion := "2.10.3"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++=
	"com.typesafe.akka" % "akka-actor_2.10" % "2.2.3" ::
	"com.typesafe.akka" % "akka-agent_2.10" % "2.2.3" ::
	"com.typesafe.akka" % "akka-testkit_2.10" % "2.2.3" ::
	"com.typesafe.akka" % "akka-remote_2.10" % "2.2.3" ::
	"org.scalatest" % "scalatest_2.10" % "2.1.3" % "test" ::
	"org.mockito" % "mockito-core" % "1.9.5" ::
	Nil


unmanagedSourceDirectories in Compile <<= (scalaSource in Compile)(Seq(_))

unmanagedSourceDirectories in Test <<= (scalaSource in Test)(Seq(_))
