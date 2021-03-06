package hk.edu.polyu.ir.groupc.searchengine.model.datasource

import java.io._
import java.util.function.Consumer

import comm.Utils
import comm.exception.{InvalidFileFormatException, RichFileNotFoundException}
import hk.edu.polyu.ir.groupc.searchengine.Debug
import hk.edu.polyu.ir.groupc.searchengine.Debug.{log, logp}
import hk.edu.polyu.ir.groupc.searchengine.model.datasource.TermIndexFactory.{FilePositionMap, TermFileMap}
import org.EditDistance

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by beenotung on 11/7/15.
  */

/**
  * @usecase ONLY TEMP data holder when parsing from file
  **/
private class RawTermInfo(val termStem: String, val fileId: Int, val logicalWordPosition: Int)


class TermEntity(val termStem: String, val filePositionMap: FilePositionMap) {
  def editDistance(another: TermEntity) = TermIndexFactory.editDistance(termStem, another.termStem)
}

class TermIndex(initMap: TermFileMap = new TermFileMap) {
  private var underlying = initMap

  def createDocumentIndex() = {
    DocumentIndexFactory.createFromTermIndex(underlying)
  }

  def cacheIDF() = {
    val docN = DocFileFactory.getDocumentCount
    underlying.foreach(termFilePosition =>
      IDFFactory.storeIDF(termFilePosition._1, IDFFactory.findIDF(docN, termFilePosition._2.size)))
    IDFFactory.updateStatis
  }

  //  @Deprecated
  //  @deprecated("used during test only")
  //  def reset() = underlying = null

  def getTermEntity(termStem: String) = underlying.get(termStem) match {
    case None => None
    case Some(filePositionMap) => Some(new TermEntity(termStem, filePositionMap))
  }

  def getFilePositionMap(term: String) = underlying.get(term)

  @Deprecated
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

  @Deprecated
  @deprecated("slow")
  /* get document(file) frequency by term */
  def getDF(term: String) = underlying.get(term) match {
    case None => 0
    case Some(filePositionMap) => filePositionMap.size
  }

  def addTerm(termInfo: RawTermInfo) = {
    underlying.getOrElseUpdate(termInfo.termStem, new FilePositionMap)
      .getOrElseUpdate(termInfo.fileId, ArrayBuffer.empty[Int])
      .+=(termInfo.logicalWordPosition)
    //    filePositionMap.getOrElse(termInfo.fileId,ArrayBuffer.empty[Int])
    //
    //    val filePositionMap = underlying.getOrElse(termInfo.termStem, {
    //      val m = new FilePositionMap
    //      underlying.put(termInfo.termStem, m)
    //      m
    //    })
    //    filePositionMap.getOrElse(termInfo.fileId, {
    //      val l = new ArrayBuffer[Int](1)
    //      filePositionMap.put(termInfo.fileId, l)
    //      l
    //    }) += termInfo.logicalWordPosition
  }

  def addTerm(term: String, fileId: Int, positions: Array[Int]) = {
    val filePositionMap = underlying.getOrElseUpdate(term, new FilePositionMap())
    val _positions = filePositionMap.getOrElse(fileId, new ArrayBuffer[Int](positions.length))
    filePositionMap.put(fileId, _positions ++ positions)
    //        val filePositionMap = underlying.getOrElse(term, {
    //          val m = new FilePositionMap
    //          underlying.put(term, m)
    //          m
    //        })
    //        filePositionMap.getOrElse(fileId, {
    //          val l = new ArrayBuffer[Int](1)
    //          filePositionMap.put(fileId, l)
    //          l
    //        }) ++= positions
  }

  def shrink() = {
    underlying.foreach(termFile => {
      val filePositionMap: FilePositionMap = new FilePositionMap
      termFile._2.foreach(filePosition => {
        filePositionMap.put(filePosition._1, filePosition._2.distinct.sortWith(_ < _))
      })
      underlying.put(termFile._1, filePositionMap)
    })
  }

  def writeToFile(filename: String) = {
    //    org.apache.commons.io.FileUtils.deleteQuietly(new File(filename))
    val out = new BufferedWriter(new FileWriter(filename))
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
  }
}

object TermIndexFactory {

  /**
    * @define key : fileId
    * @define value : positions
    **/
  type FilePositionMap = mutable.HashMap[Int, ArrayBuffer[Int]]

  /**
    * @define key : term
    * @define value : FilePositionMap [fileId, positionList]
    **/
  type TermFileMap = mutable.HashMap[String, FilePositionMap]
  /* index by term
 * content : HastMap [file id -> List[position] ]
 * example : apple -> (d1->1,2,3),(d2->2,3,4)
 * */
  private var cachedInstance: TermIndex = null

  /**
    * @param file : post file
    **/
  @throws(classOf[RichFileNotFoundException])
  def build(file: File) = {
    cachedInstance = null
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
          if ((p - lp) > Debug.process_step) {
            logp(p * 100f + "% \t" + Utils.getRamUsageString)
            lp = p
          }
          val post = createFromString(t)
          termIndex.addTerm(post)
        }
      })
      log("start shrinking index")
      termIndex.shrink()
      log("shrieked")
      cachedInstance = termIndex
    } catch {
      case e: FileNotFoundException => throw new RichFileNotFoundException(file)
    }
  }

  private def createFromString(rawString: String): RawTermInfo = {
    val xs = rawString.split(" ")
    new RawTermInfo(xs(0).toLowerCase, xs(1).toInt, xs(2).toInt)
  }

  @throws(classOf[InvalidFileFormatException])
  @throws(classOf[RichFileNotFoundException])
  def load(file: File) = {
    cachedInstance = null
    try {
      val termFileMap = new TermFileMap
      var lineLeft = -2
      /* tmp vars */
      var filePositionMap: FilePositionMap = null
      val N = Utils.countLines(file)
      var i = 0
      var lp = 0f
      var p = 0f
      Utils.processLines(file, new Consumer[String] {
        override def accept(line: String): Unit = {
          i += 1
          p = 1f * i / N
          if ((p - lp) > Debug.process_step) {
            logp(p * 100f + "% \t" + Utils.getRamUsageString)
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
              val fileId = words.head.toInt
              val positions = words.tail.map(_.toInt)
              filePositionMap.put(fileId, ArrayBuffer.empty[Int] ++ positions)
          }
        }
      })
      cachedInstance = new TermIndex(termFileMap)
    } catch {
      case e: NumberFormatException => throw new InvalidFileFormatException(file)
      case e: FileNotFoundException => throw new RichFileNotFoundException(file)
    }
  }

  @throws(classOf[IllegalStateException])
  def getTermIndex: TermIndex = {
    if (cachedInstance == null) throw new IllegalStateException("index has not been loaded")
    cachedInstance
  }


  def editDistance(term1: String, term2: String) = {
    //    EditDistance.editDistance(term1, term2)
    //    import sbt.complete.EditDistance
    EditDistance.levenshtein(term1, term2,
      insertCost = 2,
      deleteCost = 3,
      subCost = 1,
      transposeCost = 3,
      matchCost = 0,
      caseCost = 0,
      transpositions = false)
  }
}
