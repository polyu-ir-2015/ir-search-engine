package hk.edu.polyu.ir.groupc.searchengine.model.datasource

import java.io._
import java.util.function.Consumer

import comm.Utils
import comm.exception.{InvalidFileFormatException, RichFileNotFoundException}
import hk.edu.polyu.ir.groupc.searchengine.Debug
import hk.edu.polyu.ir.groupc.searchengine.model.datasource.TermInfoFactory.{FilePositionMap, TermFileMap}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * Created by beenotung on 11/7/15.
  */

/**
  * @usecase ONLY TEMP data holder when parsing from file
  **/
private class RawTermInfo(val term: String, val fileId: Int, val logicalWordPosition: Int)


class TermEntity(val termStem: String, val filePositionMap: FilePositionMap)

class TermIndex(initMap: TermFileMap = new TermFileMap) {
  private var underlying = initMap

  def getFilePositionMap(term: String) = underlying.get(term)

  @deprecated("slow")
  def getTF(term: String, fileId: Int): Int = underlying.get(term) match {
    case None => 0
    case Some(filePositionMap) => getTF(new TermEntity(term, filePositionMap), fileId)
  }

  /* get term frequency by in document (file) */
  def getTF(termEntity: TermEntity, fileid: Int): Int = termEntity.filePositionMap.get(fileid) match {
    case None => 0
    case Some(positionList) => positionList.length
  }

  /* get document(file) frequency by term */
  def getDF(termEntity: TermEntity, fileId: Int) = termEntity.filePositionMap.count(x => x._1.==(fileId))

  @deprecated("slow")
  def getDF(term: String, fileId: Int) = underlying.get(term) match {
    case None => 0
    case Some(filePositionMap) => filePositionMap.count(x => x._1 == fileId)
  }

  def addTerm(termInfo: RawTermInfo) = {
    underlying.getOrElseUpdate(termInfo.term, new FilePositionMap)
      .getOrElseUpdate(termInfo.fileId, new ListBuffer[Int])
      .+=:(termInfo.logicalWordPosition)
  }

  def shrink() = {
    val newInstance = new TermFileMap
    underlying.foreach(termFile => {
      val filePositionMap: FilePositionMap = new FilePositionMap
      termFile._2.foreach(filePosition => {
        filePositionMap.put(filePosition._1, filePosition._2.distinct.sortWith(_ < _))
      })
      newInstance.put(termFile._1, filePositionMap)
    })
    underlying = newInstance
  }

  def writeToFile(filename: String) = {
    //    org.apache.commons.io.FileUtils.deleteQuietly(new File(filename))
    val file = new File(filename)
    val out = new BufferedWriter(new FileWriter(file))
    out.write("# term, number of files\n")
    out.write("# fileId, position...\n")
    underlying.foreach(termFile => {
      out.write(termFile._1 + " " + termFile._2.size)
      termFile._2.foreach(filePositionMap => {
        out.write("\n" + filePositionMap._1)
        filePositionMap._2.foreach(p => out.write(" " + p))
      }
      )
      out.write("\n")
    }
    )
    out.close()
    //    println(underlying.toString())
    //    output.write(underlying.toString())
  }
}

object TermInfoFactory {

  /**
    * @define key : fileId
    * @define value : positionList
    **/
  type FilePositionMap = mutable.HashMap[Int, ListBuffer[Int]]

  /**
    * @define key : term
    * @define value : FilePositionMap [fileId, positionList]
    **/
  type TermFileMap = mutable.HashMap[String, FilePositionMap]

  /* index by term
 * content : HastMap [file id -> List[position] ]
 * example : apple -> (d1->1,2,3),(d2->2,3,4)
 * */
  private var cachedTermIndex: TermIndex = null

  /**
    * @param file : post file
    **/
  @throws(classOf[RichFileNotFoundException])
  def build(file: File) = {
    try {
      val termIndex = new TermIndex
      val N = Utils.countLines(file)
      var i = 0
      var lp = 0f
      var p = 0f
      Utils.processLines(file, new Consumer[String] {
        override def accept(t: String): Unit = {
          i += 1
          p = 1f * i / N
          if ((p - lp) > 1f / 100f) {
            Debug.log(p * 100f + "% "
              + Runtime.getRuntime.totalMemory() / 1024 / 1024 + "M / "
              + Runtime.getRuntime.maxMemory() / 1024 / 1024 + "M")
            lp = p
          }
          val post = createFromString(t)
          termIndex.addTerm(post)
        }
      })
      termIndex.shrink()
      cachedTermIndex = termIndex
    } catch {
      case e: FileNotFoundException => throw new RichFileNotFoundException(file)
    }
  }

  private def createFromString(rawString: String): RawTermInfo = {
    val xs = rawString.split(" ")
    new RawTermInfo(xs(0).toLowerCase, xs(1).toInt, xs(2).toInt)
  }

  private val MODE_EMPTY = 0
  private val MODE_COMMENT = -1
  private val MODE_READING = 1

  @throws(classOf[InvalidFileFormatException])
  @throws(classOf[RichFileNotFoundException])
  def load(file: File) = {
    try {
      val termFileMap = new TermFileMap
      var lineLeft = -2
      /* tmp vars */
      var filePositionMap: FilePositionMap = null
      val termIndex = new TermIndex
      val N = Utils.countLines(file)
      var i = 0
      var lp = 0f
      var p = 0f
      Utils.processLines(file, new Consumer[String] {
        override def accept(line: String): Unit = {
          i += 1
          p = 1f * i / N
          if ((p - lp) > 1f / 100f) {
            Debug.log(p * 100f + "% "
              + Runtime.getRuntime.totalMemory() / 1024 / 1024 + "M / "
              + Runtime.getRuntime.maxMemory() / 1024 / 1024 + "M")
            lp = p
          }
          lineLeft match {
            case -2 | -1 =>
              lineLeft += 1
            case 0 =>
              val words = line.split(" ")
              val term = words(0)
              lineLeft = words(1).toInt
              filePositionMap = new FilePositionMap
              termFileMap.put(term, filePositionMap)
            case _ =>
              lineLeft -= 1
              val words = line.split(" ")
              val fileId = words(0).toInt
              val positionList = ListBuffer.newBuilder[Int].++=(words.tail.map(s => s.toInt)).result()
              filePositionMap.put(fileId, positionList)
          }
        }
      })
      cachedTermIndex = new TermIndex(termFileMap)
    } catch {
      case e: NumberFormatException => throw new InvalidFileFormatException(file)
      case e: FileNotFoundException => throw new RichFileNotFoundException(file)
    }
  }

  @throws(classOf[IllegalStateException])
  def getTermIndex: TermIndex = {
    if (cachedTermIndex == null) throw new IllegalStateException("index has not been loaded")
    cachedTermIndex
  }
}
