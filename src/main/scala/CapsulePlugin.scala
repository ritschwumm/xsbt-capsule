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
	val capsuleArgs					= settingKey[Seq[String]]("arguments in the command line")
	val capsuleMinJavaVersion		= settingKey[Option[String]]("minimum java version")
	val capsuleMakeExecutable		= settingKey[Boolean]("make the jar file executable on unixoid systems")

	val capsuleExtras				= taskKey[Traversable[PathMapping]]("additional resources as a task to allow inclusion of packaged wars etc.")
}

object CapsulePlugin extends AutoPlugin {
	//------------------------------------------------------------------------------
	//## constants
	
	private val capsuleClassName		= "Capsule"
	private val capsuleFileName			= capsuleClassName + ".class"
	private val capsuleClassResource	= "/" + capsuleFileName
	private val execHeaderResource		= "/exec-header.sh"
	
	//------------------------------------------------------------------------------
	//## exports
	
	override val requires:Plugins		= ClasspathPlugin && plugins.JvmPlugin
	
	override val trigger:PluginTrigger	= noTrigger

	lazy val autoImport	= Import
	import autoImport._
	
	override lazy val projectSettings:Seq[Def.Setting[_]]	=
			Vector(
				capsule			:=
						buildTask(
							streams				= Keys.streams.value,
							assets				= classpathAssets.value,
							jarFile				= capsuleJarFile.value,
							applicationName		= Keys.name.value,
							applicationVersion	= Keys.version.value,
							applicationClass	= capsuleMainClass.value,			
							vmOptions			= capsuleVmOptions.value,
							systemProperties	= capsuleSystemProperties.value,
							args				= capsuleArgs.value,
							minJavaVersion		= capsuleMinJavaVersion.value,
							makeExecutable		= capsuleMakeExecutable.value,
							extras				= capsuleExtras.value
						),
				capsuleBuildDir				:= Keys.crossTarget.value / "capsule",
				capsuleJarFile				:= capsuleBuildDir.value / (capsulePackageName.value + ".jar"),
				
				capsulePackageName			:= Keys.name.value + "-" + Keys.version.value,
				capsuleMainClass			:= (Keys.mainClass in Runtime).value,
				capsuleVmOptions			:= Seq.empty,
				capsuleSystemProperties		:= Map.empty,
				capsuleArgs					:= Seq.empty,
				capsuleMinJavaVersion		:= None,
				capsuleMakeExecutable		:= false,
				
				capsuleExtras				:= Seq.empty
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
		args:Seq[String],
		minJavaVersion:Option[String],
		makeExecutable:Boolean,
		extras:Traversable[PathMapping]
	):File =
			IO withTemporaryDirectory { tempDir =>
				val applicationClassGot:String	=
						applicationClass	getOrElse {
							xu.fail logging (streams, s"${capsuleMainClass.key.label} must be set")
						}
				val minJavaVersionGot:String	=
						minJavaVersion		getOrElse {
							xu.fail logging (streams, s"${capsuleMinJavaVersion.key.label} must be set")
						}
						
				val capsuleClassFile:File	= tempDir / capsuleFileName
				IO download (
					xu.classpath url capsuleClassResource,
					capsuleClassFile
				)
				
				val jarSources:Traversable[PathMapping]	=
						Vector(capsuleClassFile -> capsuleFileName) ++
						(assets map { _.flatPathMapping })			++
						extras
					
				val manifest	=
						xu.jar manifest (
							MANIFEST_VERSION.toString	-> "1.0",
							MAIN_CLASS.toString			-> capsuleClassName,
							"Application-Name"			-> applicationName,
							"Application-Version"		-> applicationVersion,
							"Application-Class"			-> applicationClassGot,
							"System-Properties"			-> (systemProperties map { case (k, v) => k + "=" + v } mkString " "),
							"JVM-Args"					-> (vmOptions mkString " "),
							"Args"						-> (args mkString " "),
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
