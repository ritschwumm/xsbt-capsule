package xsbtCapsule

import sbt._
import Keys.TaskStreams

import xsbtUtil._
import xsbtClasspath.{ Asset => ClasspathAsset, ClasspathPlugin }
import xsbtClasspath.Import.classpathAssets

object Import {
	val capsule						= taskKey[File]("complete build, returns the created capsule jar")
	val capsuleOutputDir			= taskKey[File]("where to put the capsule jar")
	val capsuleJarName				= taskKey[String]("the name of the capsule jar")
	val capsuleJarFile				= taskKey[File]("the capsule jar file")
	val capsuleMainClass			= taskKey[Option[String]]("name of the main class")
	val capsuleVmOptions			= settingKey[Seq[String]]("vm options like -Xmx128")
	val capsuleSystemProperties		= settingKey[Map[String,String]]("-D in the command line")
	val capsuleMinJavaVersion		= settingKey[Option[String]]("minimum java version")
	val capsulePrependExecHeader	= settingKey[Boolean]("include exec header")
}

object CapsulePlugin extends AutoPlugin {
	//------------------------------------------------------------------------------
	//## constants
	
	private val capsuleClassName		= "Capsule.class"
	
	private val capsuleClassResource	= "/Capsule.class"
	private val execHeaderResource		= "/exec-header.sh"
	
	//------------------------------------------------------------------------------
	//## exports
	
	override def requires:Plugins		= ClasspathPlugin
	
	override def trigger:PluginTrigger	= allRequirements

	lazy val autoImport	= Import
	import autoImport._
	
	override def projectSettings:Seq[Def.Setting[_]]	=
			Vector(
				capsule		:=
						buildTask(
							streams				= Keys.streams.value,
							assets				= classpathAssets.value,
							jarFile				= capsuleJarFile.value,
							applicationName		= Keys.version.value,
							applicationVersion	= Keys.name.value,
							applicationClass	= capsuleMainClass.value,			
							vmOptions			= capsuleVmOptions.value,
							systemProperties	= capsuleSystemProperties.value,
							minJavaVersion		= capsuleMinJavaVersion.value,
							prependExecHeader	= capsulePrependExecHeader.value
						),
				capsuleOutputDir			:= Keys.crossTarget.value / "capsule",
				capsuleJarName				:= Keys.name.value + "-" + Keys.version.value + ".jar",
				capsuleJarFile				:= capsuleOutputDir.value / capsuleJarName.value,
				capsuleMainClass			:= Keys.mainClass.value,
				capsuleVmOptions			:= Seq.empty,
				capsuleSystemProperties		:= Map.empty,
				capsuleMinJavaVersion		:= None,
				capsulePrependExecHeader	:= false
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
		prependExecHeader:Boolean
	):File =
			IO withTemporaryDirectory { tempDir =>
				val applicationClassGot	=
						applicationClass	getOrElse
						failWithError(streams, s"${capsuleMainClass.key.label} must be set")
				val minJavaVersionGot	=
						minJavaVersion		getOrElse
						failWithError(streams, s"${capsuleMinJavaVersion.key.label} must be set")
						
				val capsuleClassFile	= tempDir / capsuleClassName
				IO download (
					resourceURL(capsuleClassResource),
					capsuleClassFile
				)
				
				val jarSources	=
						(capsuleClassFile -> capsuleClassName) +:
						(assets map { _.flatPathMapping })
					
				val manifest	= 
						jarManifest(
							// Attribute.Name.MANIFEST_VERSION
							"Manifest-Version"		-> "1.0",
							 // Attribute.Name.MAIN_CLASS
							"Main-Class"			-> "Capsule",
							"Application-Name"		-> applicationName,
							"Application-Version"	-> applicationVersion,
							"Application-Class"		-> applicationClassGot,
							"System-Properties"		-> (systemProperties map { case (k, v) => k + "=" + v } mkString " "),
							"JVM-Args"				-> (vmOptions mkString " "),
							"Min-Java-Version"		-> minJavaVersionGot
						)
						
				streams.log info s"building capsule file ${jarFile}"
				jarFile.mkParentDirs()
				
				if (prependExecHeader) {
					val tempJar		= tempDir / "capsule.jar"
					IO jar		(jarSources, tempJar, manifest)
					
					IO write	(jarFile, resourceBytes(execHeaderResource))
					IO append	(jarFile, IO readBytes tempJar)
				}
				else {
					IO jar (jarSources, jarFile, manifest)
				}
			
				jarFile setExecutable (true, false)
				jarFile
			}
}
