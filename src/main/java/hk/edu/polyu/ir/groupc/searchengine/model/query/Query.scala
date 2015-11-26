package hk.edu.polyu.ir.groupc.searchengine.model.query

import java.io.{File, FileNotFoundException}

import comm.exception.RichFileNotFoundException
import hk.edu.polyu.ir.groupc.searchengine.model.datasource.{StopWordFactory, TermEntity, TermIndexFactory}

import scala.collection.SeqView
import scala.io.Source

/**
  * Created by beenotung on 11/6/15.
  */
class Query(val queryId: String, val rawQueryContent: String) {
  lazy val simpleTerms = QueryFactory.extract(rawQueryContent)
  lazy val expandedTerms = QueryFactory.expand(simpleTerms flatten)

  override def toString: String = s"$queryId $rawQueryContent"
}

class ExpandedTerm(val term: TermEntity, val weight: Double)

class QueryDescription(val relevant: Boolean, val primarily: Boolean, val primarilyMentioned: Boolean)

object QueryFactory {

  private var queries_T: List[Query] = null
  private var queries_TDN: List[Query] = null

  def extract(rawStringLine: String): Array[Option[TermEntity]] = {
    //    rawStringLine split " " map stem map TermIndexFactory.getTermIndex.getTermEntity
    rawStringLine split " " map (_.toLowerCase) filter (!StopWordFactory.isStopWord(_)) map stem map TermIndexFactory.getTermIndex.getTermEntity
  }

  def stem(string: String): String = {
    val stemmer = new Stemmer()
    stemmer add(string.toCharArray, string.length)
    stemmer stem()
    stemmer.toString.toLowerCase
  }

  def expand(terms: Array[TermEntity]): Array[ExpandedTerm] = {
    terms.map(t => new ExpandedTerm(t, 1))
  }

  @throws(classOf[RichFileNotFoundException])
  def loadFromFile_T(file: File) = {
    try {
      queries_T = Source.fromFile(file).getLines().map[Query](createFromString).toList
    } catch {
      case e: FileNotFoundException => throw new RichFileNotFoundException(file)
    }
  }

  @throws(classOf[RichFileNotFoundException])
  def loadFromFile_TDN(file: File) = {
    try {
      queries_TDN = Source.fromFile(file).getLines().map[Query](createFromString).toList
    } catch {
      case e: FileNotFoundException => throw new RichFileNotFoundException(file)
    }
  }

  private def createFromString(rawString: String): Query = {
    val id = rawString.substring(0, 3)
    val content = rawString.substring(4, rawString.length)
    new Query(id.trim, content.trim)
  }

  def ready = (queries_T != null) && (queries_TDN != null)

  def getQueries_T: SeqView[Query, List[Query]] = {
    if (queries_T == null) throw new IllegalStateException("query_T has not been loaded")
    queries_T.view
  }

  def getQueries_TDN: SeqView[Query, List[Query]] = {
    if (queries_TDN == null) throw new IllegalStateException("query_TDN has not been loaded")
    queries_TDN.view
  }
}

