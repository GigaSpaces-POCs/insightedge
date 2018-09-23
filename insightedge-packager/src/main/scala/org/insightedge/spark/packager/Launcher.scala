/*
 * Copyright (c) 2016, GigaSpaces Technologies, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.insightedge.spark.packager

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar

import org.apache.commons.io.filefilter.TrueFileFilter
import org.insightedge.spark.packager.Utils._
import sys.process._

/**
  * @author Leonid_Poliakov
  */
object Launcher {

  def main(args: Array[String]) {
    val project = parameter("Project folder" -> "project.directory")
    /* variables that used only in Launcher.scala in xap-premium project
    val version = parameter("InsightEdge version" -> "insightedge.version")
    val milestone = parameter("Project milestone" -> "insightedge.milestone")
    val buildNumber = parameter("Project build number" -> "insightedge.build.number")
    val artifactVersion = parameter("Project maven artifact version" -> "project.version")
    val xapVersion = parameter("XAP version" -> "xap.version") */
    val edition = parameter("Distribution edition" -> "dist.edition")
    val lastCommitHash = optionalParameter("Last commit hash" -> "last.commit.hash")
    val output = parameter("Output folder" -> "output.exploded.directory")
    val outputFile = parameter("Output file" -> "output.compressed.file")
    val outputPrefix = parameter("Output contents prefix" -> "output.contents.prefix")
    val spark = parameter("Spark distribution" -> "dist.spark")
    val grid = getXapLocation(edition, project)
    val zeppelin = parameter("Zeppelin distribution" -> "dist.zeppelin")
    val examplesTarget = parameter("Examples target folder" -> "dist.examples.target")
    val resources = s"$project/insightedge-packager/src/main/resources"
    val zeppelinResources = s"$project/insightedge-zeppelin/src/main/resources"
    val templates = s"datagrid/deploy/templates"
    val insightEdgeHome = s"$output/insightedge"
    val examplesJar = "insightedge-examples.jar"
    val insightedgePackagerTargetPath = s"$project/insightedge-packager/target/"
    val xapJdbcExtZip = "xap-jdbc-insightedge-extension.zip"

    println(s"grid is [$grid]")
    println(s"insightedgePackagerTargetPath is [$insightedgePackagerTargetPath]")
    validateHash(lastCommitHash)
    remove(output)
    run("Unpacking datagrid " + grid + " to   " + output) {
      unzip(grid, s"$output", cutRootFolder = true)
    }
    buildInsightEdge()

    def buildInsightEdge() {
      run("Adding integration scripts") {
        copy(s"$resources/bin", s"$output/bin")
        copy(s"$resources/insightedge/bin", s"$insightEdgeHome/bin")
      }
      run("Adding integration libs") {
        copy(s"$project/insightedge-core/target", s"$insightEdgeHome/lib", nameFilter(n => n.startsWith("insightedge-core") && !n.contains("test") && !n.contains("sources") && !n.contains("javadoc")))
        copy(s"$project/insightedge-cli/target", s"$output/tools/cli", nameFilter(n => n.startsWith("insightedge-cli") && !n.contains("test") && !n.contains("sources") && !n.contains("javadoc")))
      }
      run("Adding poms of integration libs") {
        copy(s"$project/pom.xml", s"$insightEdgeHome/tools/maven/poms/insightedge-package/pom.xml")
        copy(s"$project/insightedge-core/pom.xml", s"$insightEdgeHome/tools/maven/poms/insightedge-core/pom.xml")
      }
      run("Adding examples") {
        val examplesProject = s"$examplesTarget/.."

        copy(s"$examplesProject/target/$examplesJar", s"$insightEdgeHome/examples/jars/$examplesJar")
        copy(s"$examplesProject/python/sf_salaries.py", s"$insightEdgeHome/examples/python/sf_salaries.py")

        // copy all i9e-examples folder
        copy(s"$examplesProject", s"$insightEdgeHome/examples/")

        // remove unnecessary folder/files from examples folder
        remove(s"$insightEdgeHome/examples/build.sbt")
        remove(s"$insightEdgeHome/examples/dependency-reduced-pom.xml")
        remove(s"$insightEdgeHome/examples/doc")
        remove(s"$insightEdgeHome/examples/.git")
        remove(s"$insightEdgeHome/examples/.gitignore")
        remove(s"$insightEdgeHome/examples/Jenkinsfile")
        remove(s"$insightEdgeHome/examples/LICENSE.md")
        remove(s"$insightEdgeHome/examples/project")
        remove(s"$insightEdgeHome/examples/spark-warehouse")
        remove(s"$insightEdgeHome/examples/tools")
        remove(s"$insightEdgeHome/examples/target")
        remove(s"$insightEdgeHome/examples/.idea")
        remove(s"$insightEdgeHome/examples/insightedge-examples.iml")
        remove(s"$insightEdgeHome/examples/src/test")
        remove(s"$insightEdgeHome/examples/xap")
        remove(s"$insightEdgeHome/examples/transaction.log")
        remove(s"$insightEdgeHome/examples/README.md")
      }
      run("Adding InsightEdge resources") {
        copy(s"$resources/insightedge/conf/", s"$insightEdgeHome/conf")
        copy(s"$resources/insightedge/data/", s"$insightEdgeHome/data")
      }
      run("Unpacking Zeppelin") {
        untgz(zeppelin, s"$insightEdgeHome/zeppelin", cutRootFolder = true)
      }

      run("Configuring Zeppelin") {
        copy(s"$zeppelinResources/zeppelin/bin/interpreter.cmd", s"$insightEdgeHome/zeppelin/bin/interpreter.cmd")
        copy(s"$zeppelinResources/zeppelin/config/zeppelin-site.xml", s"$insightEdgeHome/zeppelin/conf/zeppelin-site.xml")
        copy(s"$zeppelinResources/zeppelin/config/zeppelin-env.sh", s"$insightEdgeHome/zeppelin/conf/zeppelin-env.sh")
        copy(s"$zeppelinResources/zeppelin/config/zeppelin-env.cmd", s"$insightEdgeHome/zeppelin/conf/zeppelin-env.cmd")
        remove(s"$insightEdgeHome/zeppelin/interpreter/spark/dep") ///delete in the future when delete zepplin spark interperter
      }
      run("Adding Zeppelin notes") {
        copy(s"$zeppelinResources/zeppelin/notes", s"$insightEdgeHome/zeppelin/notebook")
      }

      run("Installing Zeppelin interperters" ){
        val script = s"$insightEdgeHome/zeppelin/bin/install-interpreter.sh"
        permissions(script,read = Some(true), write = None, execute = Some(true))
        s"$script --name md,jdbc" !
      }

      run("Adding Zeppelin define interpreter"){
        copy(s"$zeppelinResources/zeppelin/interpreter-setting.json",s"$insightEdgeHome/zeppelin/interpreter/spark/interpreter-setting.json")
        copy(s"$project/insightedge-zeppelin/target/insightedge-zeppelin.jar", s"$insightEdgeHome/lib/insightedge-zeppelin.jar")
      }

      run("Adding Hadoop winutils") {
        unzip(s"$resources/insightedge/winutils/hadoop-winutils-2.6.0.zip", s"$insightEdgeHome/tools/winutils", cutRootFolder = true)
      }
      run("Unpacking spark") {
        untgz(spark, s"$insightEdgeHome/spark", cutRootFolder = true)
      }
      run("Injecting InsightEdge spark overrides") {
        copy(s"$resources/insightedge/spark/", s"$insightEdgeHome/spark")
      }
      run("copy cli auto complete script") {
        copy(s"$project/insightedge-cli/target/insightedge-autocomplete", s"$insightEdgeHome/../tools/cli/insightedge-autocomplete")
      }
      run("Making scripts executable") {
        permissions(s"$output/bin/", read = Some(true), write = Some(true), execute = Some(true))
        permissions(s"$insightEdgeHome/bin/", read = Some(true), write = Some(true), execute = Some(true))
        permissions(s"$insightEdgeHome/spark/bin/", read = Some(true), write = Some(true), execute = Some(true))
        permissions(output, fileFilter = nameFilter(n => n.endsWith(".sh") || n.endsWith(".cmd") || n.endsWith(".bat")), dirFilter = TrueFileFilter.INSTANCE, read = Some(true), write = None, execute = Some(true))
      }

      if (edition equals ("open")) {
        run("remove insightedge script from open packing") {
          remove(s"$insightEdgeHome/bin/insightedge")
          remove(s"$insightEdgeHome/bin/insightedge.cmd")
        }
      }

      run("Packing installation") {
        new File(outputFile).getParentFile.mkdirs()
        zip(output, outputFile, outputPrefix)
      }

    }
  }

  def getXapLocation(edition: String, projectBasedir: String): String = {
    println("edition: " + edition)
    val prefix = s"$projectBasedir/insightedge-packager/target/"
    edition match {
      case "premium" => prefix + "xap-premium.zip"
      case "open" => prefix + "xap-open.zip"
      case _ => throw new IllegalArgumentException("Illegal edition: " + edition + ", XAP edition can be premium or open")
    }
  }

  def parameter(tuple: (String, String)): String = {
    val (label, key) = tuple
    val value = Option(System.getProperty(key))
    require(value.isDefined, s"$key ($label) must be set as environment variable")
    println(s"$label: ${value.get}")
    value.get
  }

  def optionalParameter(tuple: (String, String)): Option[String] = {
    val (label, key) = tuple
    val value = Option(System.getProperty(key)).filter(!_.isEmpty)
    println(s"$label: ${value.getOrElse("")}")
    value
  }

  def run(name: String)(block: => Unit): Unit = {
    println(name + "...")
    val start = System.currentTimeMillis()
    block
    println("\tdone in " + (System.currentTimeMillis() - start) + " ms")
  }

}
