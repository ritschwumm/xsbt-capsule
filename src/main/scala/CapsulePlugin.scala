import sbt._

import java.util.jar.{ Manifest => JarManifest }
import java.util.jar.Attributes.{ Name => AttrName }
	
import Keys.{ Classpath, TaskStreams }

import ClasspathPlugin._

object CapsulePlugin extends Plugin {
	private val capsuleData			= taskKey[Data]("build data helper")
	
	//------------------------------------------------------------------------------
	//## exported keys
	
	val capsule						= taskKey[File]("complete build, returns the created capsule jar")
	val capsuleOutput				= taskKey[File]("where to put the capsule jar")
	val capsuleName					= taskKey[String]("the name of the capsule jar")
	val capsuleJarFile				= taskKey[File]("the capsule jar file")
	val capsuleMainClass			= taskKey[Option[String]]("name of the main class")
	val capsuleVmOptions			= settingKey[Seq[String]]("vm options like -Xmx128")
	val capsuleSystemProperties		= settingKey[Map[String,String]]("-D in the command line")
	val capsuleMinJavaVersion		= settingKey[Option[String]]("minimum java version")
	val capsulePrependExecHeader	= settingKey[Boolean]("include exec header")
	
	lazy val capsuleSettings:Seq[Def.Setting[_]]	= 
			classpathSettings ++ 
			Vector(
				capsuleData	:=
						Data(
							jarFile				= capsuleJarFile.value,
							applicationName		= Keys.version.value,
							applicationVersion	= Keys.name.value,
							applicationClass	= capsuleMainClass.value,			
							vmOptions			= capsuleVmOptions.value,
							systemProperties	= capsuleSystemProperties.value,
							minJavaVersion		= capsuleMinJavaVersion.value,
							prependExecHeader	= capsulePrependExecHeader.value
						),
				capsule		:=
						buildTaskImpl(
							streams	= Keys.streams.value,
							assets	= classpathAssets.value,
							data	= capsuleData.value
						),
						
				capsuleOutput				:= Keys.crossTarget.value / "capsule",
				capsuleName					:= Keys.name.value + "-" + Keys.version.value + ".jar",
				capsuleJarFile				:= capsuleOutput.value / capsuleName.value,
				capsuleMainClass			:= Keys.mainClass.value,
				capsuleVmOptions			:= Seq.empty,
				capsuleSystemProperties		:= Map.empty,
				capsuleMinJavaVersion		:= None,
				capsulePrependExecHeader	:= false
			)
	
	//------------------------------------------------------------------------------
	//## data task
	
	private case class Data(
		jarFile:File,
		applicationName:String,
		applicationVersion:String,
		applicationClass:Option[String],
		vmOptions:Seq[String],
		systemProperties:Map[String,String],
		minJavaVersion:Option[String],
		prependExecHeader:Boolean
	)
	
	//------------------------------------------------------------------------------
	//## build task
	
	private def buildTaskImpl(
		streams:TaskStreams,	
		assets:Seq[ClasspathAsset],
		data:Data
	):File =
			IO withTemporaryDirectory { tempDir =>
				val capsuleClassName	= "Capsule.class"
				val capsuleClassFile	= tempDir / capsuleClassName
				IO download (
					getClass getResource capsuleClassName,
					capsuleClassFile
				)
				
				val execHeaderName		= "exec-header.sh"
				val execHeaderStream	= getClass getResourceAsStream execHeaderName
				val execHeaderBytes	= IO readBytes execHeaderStream
				execHeaderStream.close()
				
				val jarSources	=
						(capsuleClassFile -> capsuleClassName) +: 
						(assets map { asset => asset.jar -> asset.name })
					
				val manifest	= 
						jarManifest(
							// AName's MANIFEST_VERSION and MAIN_CLASS
							"Manifest-Version"		-> "1.0",
							"Main-Class"			-> "Capsule",
							"Application-Name"		-> data.applicationName,
							"Application-Version"	-> data.applicationVersion,
							"Application-Class"		-> (data.applicationClass getOrElse (sys error "applicationClass must be set")),
							"System-Properties"		-> (data.systemProperties map { case (k, v) => k + "=" + v } mkString " "),
							"JVM-Args"				-> (data.vmOptions mkString " "),
							"Min-Java-Version"		-> (data.minJavaVersion getOrElse (sys error "minJavaVersion must be set"))
						)
						
				streams.log info s"building capsule file ${data.jarFile}"
				IO createDirectory data.jarFile.getParentFile
				
				if (data.prependExecHeader) {
					val tempJar		= tempDir / "capsule.jar"
					IO jar		(jarSources, tempJar, manifest)
					IO write	(data.jarFile, execHeaderBytes)
					IO append	(data.jarFile, IO readBytes tempJar)
				}
				else {
					IO jar (jarSources, data.jarFile, manifest)
				}
			
				data.jarFile setExecutable (true, false)
				data.jarFile
			}
			
	private def jarManifest(attrs:(String,String)*):JarManifest	= {
		val manifest	= new JarManifest
		attrs foreach { case (k, v) =>
			manifest.getMainAttributes put (new AttrName(k), v)
		}
		manifest
	}
}
