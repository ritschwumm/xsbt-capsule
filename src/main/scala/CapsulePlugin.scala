package xsbtCapsule

import java.util.jar.Attributes.Name._

import sbt._
import Keys.TaskStreams

import xsbtUtil.implicits._
import xsbtUtil.types._
import xsbtUtil.{ util => xu }

import xsbtClasspath.{ Asset => ClasspathAsset, ClasspathPlugin }
import xsbtClasspath.Import.classpathAssets

object Import {
	val capsuleBuildDir				= settingKey[File]("base directory of built files")
	val capsulePackageName			= settingKey[String]("name of the package built")
	
	val capsule						= taskKey[File]("complete build, returns the created capsule jar")
	val capsuleJarFile				= taskKey[File]("the capsule jar file")
	
	val capsuleMainClass			= taskKey[Option[String]]("name of the main class")
	val capsuleVmOptions			= settingKey[Seq[String]]("vm options like -Xmx128")
	val capsuleSystemProperties		= settingKey[Map[String,String]]("-D in the command line")
	val capsuleMinJavaVersion		= settingKey[Option[String]]("minimum java version")
	val capsuleMakeExecutable		= settingKey[Boolean]("make the jar file executable on unixoid systems")
}

object CapsulePlugin extends AutoPlugin {
	//------------------------------------------------------------------------------
	//## constants
	
	private val capsuleClassName		= "Capsule.class"
	
	private val capsuleClassResource	= "/Capsule.class"
	private val execHeaderResource		= "/exec-header.sh"
	
	//------------------------------------------------------------------------------
	//## exports
	
	override val requires:Plugins		= ClasspathPlugin && plugins.JvmPlugin
	
	override val trigger:PluginTrigger	= noTrigger

	lazy val autoImport	= Import
	import autoImport._
	
	override lazy val projectSettings:Seq[Def.Setting[_]]	=
			Vector(
				capsuleBuildDir			:= Keys.crossTarget.value / "capsule",
				
				capsule		:=
						buildTask(
							streams				= Keys.streams.value,
							assets				= classpathAssets.value,
							jarFile				= capsuleJarFile.value,
							applicationName		= Keys.name.value,
							applicationVersion	= Keys.version.value,
							applicationClass	= capsuleMainClass.value,			
							vmOptions			= capsuleVmOptions.value,
							systemProperties	= capsuleSystemProperties.value,
							minJavaVersion		= capsuleMinJavaVersion.value,
							makeExecutable		= capsuleMakeExecutable.value
						),
				capsuleJarFile				:= capsuleBuildDir.value / (capsulePackageName.value + ".jar"),
				
				capsulePackageName			:= Keys.name.value + "-" + Keys.version.value,
				capsuleMainClass			:= (Keys.mainClass in Runtime).value,
				capsuleVmOptions			:= Seq.empty,
				capsuleSystemProperties		:= Map.empty,
				capsuleMinJavaVersion		:= None,
				capsuleMakeExecutable		:= false
			)
	
	//------------------------------------------------------------------------------
	//## tasks
	
	private def buildTask(
		streams:TaskStreams,	
		assets:Seq[ClasspathAsset],
		jarFile:File,
		applicationName:String,
		applicationVersion:String,
		applicationClass:Option[String],
		vmOptions:Seq[String],
		systemProperties:Map[String,String],
		minJavaVersion:Option[String],
		makeExecutable:Boolean
	):File =
			IO withTemporaryDirectory { tempDir =>
				val applicationClassGot	=
						applicationClass	getOrElse {
							xu.fail logging (streams, s"${capsuleMainClass.key.label} must be set")
						}
				val minJavaVersionGot	=
						minJavaVersion		getOrElse {
							xu.fail logging (streams, s"${capsuleMinJavaVersion.key.label} must be set")
						}
						
				val capsuleClassFile	= tempDir / capsuleClassName
				IO download (
					xu.classpath url capsuleClassResource,
					capsuleClassFile
				)
				
				val jarSources	=
						(capsuleClassFile -> capsuleClassName) +:
						(assets map { _.flatPathMapping })
					
				val manifest	= 
						xu.jar manifest (
							MANIFEST_VERSION.toString	-> "1.0",
							MAIN_CLASS.toString			-> "Capsule",
							"Application-Name"			-> applicationName,
							"Application-Version"		-> applicationVersion,
							"Application-Class"			-> applicationClassGot,
							"System-Properties"			-> (systemProperties map { case (k, v) => k + "=" + v } mkString " "),
							"JVM-Args"					-> (vmOptions mkString " "),
							"Min-Java-Version"			-> minJavaVersionGot
						)
						
				streams.log info s"building capsule file ${jarFile}"
				jarFile.mkParentDirs()
				
				if (makeExecutable) {
					val tempJar	= tempDir / "capsule.jar"
					IO jar		(jarSources, tempJar, manifest)
					
					IO write	(jarFile, xu.classpath bytes execHeaderResource)
					IO append	(jarFile, IO readBytes tempJar)
					
					jarFile setExecutable (true, false )
				}
				else {
					IO jar (jarSources, jarFile, manifest)
				}
			
				jarFile
			}
}
