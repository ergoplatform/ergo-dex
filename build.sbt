import sbt.Keys.publishMavenStyle

enablePlugins(GraalVMNativeImagePlugin)

lazy val sonatypePublic = "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
lazy val sonatypeReleases = "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
lazy val sonatypeSnapshots = "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers ++= Seq(Resolver.mavenLocal, sonatypeReleases, sonatypeSnapshots, Resolver.mavenCentral)

scalaVersion := "2.12.10"
version := "0.1.0"

val appkitVersion = "develop-fa13b89f-SNAPSHOT"
val appkit = "org.ergoplatform" %% "ergo-appkit" % appkitVersion

val ergoContractsVersion = "0.0.0-15-df0325c9-SNAPSHOT"

val mockitoScalaVerstion = "1.11.4"

libraryDependencies ++= Seq(
  appkit, (appkit % Test).classifier("tests"),
  //.classifier("tests-sources") // uncomment this for debuging to make sources available (doesn't work when appkit is published locally) ,
  "org.graalvm.sdk" % "graal-sdk" % "19.2.1",
  "com.squareup.okhttp3" % "mockwebserver" % "3.12.0",
  "org.ergoplatform" %% "verified-contracts" % ergoContractsVersion,
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.1" % "test",
  "org.mockito" %% "mockito-scala" % mockitoScalaVerstion % "test",
  "org.mockito" %% "mockito-scala-scalatest" % mockitoScalaVerstion % "test"
)

publishMavenStyle in ThisBuild := true

publishArtifact in Test := false

fork in Test := false

pomExtra in ThisBuild :=
  <developers>
    <developer>
      <id>aslesarenko</id>
      <name>Alexander Slesarenko</name>
      <url>https://github.com/aslesarenko/</url>
    </developer>
  </developers>

// set bytecode version to 8 to fix NoSuchMethodError for various ByteBuffer methods
// see https://github.com/eclipse/jetty.project/issues/3244
// these options applied only in "compile" task since scalac crashes on scaladoc compilation with "-release 8"
// see https://github.com/scala/community-builds/issues/796#issuecomment-423395500
scalacOptions in(Compile, compile) ++= (if (scalaBinaryVersion.value == "2.11") Seq() else Seq("-release", "8"))

test in assembly := {}
assemblyJarName in assembly := s"ergodex-${version.value}.jar"

// See https://www.scala-sbt.org/sbt-native-packager/formats/graalvm-native-image.html
graalVMNativeImageOptions ++= Seq(
  "-H:ResourceConfigurationFiles=" + baseDirectory.value / "graal" / "META-INF" / "native-image" / "resource-config.json",
  "-H:ReflectionConfigurationFiles=" + baseDirectory.value / "graal" / "META-INF" / "native-image" / "reflect-config.json",
  "--no-server",
  "--report-unsupported-elements-at-runtime",
  "--no-fallback",
  "-H:+TraceClassInitialization",
  "-H:+ReportExceptionStackTraces",
  "-H:+AddAllCharsets",
  "-H:+AllowVMInspection",
  "-H:-RuntimeAssertions",
  "--allow-incomplete-classpath",
  "--enable-url-protocols=http,https"
)
