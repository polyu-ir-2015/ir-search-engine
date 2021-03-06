package hk.edu.polyu.ir.groupc.searchengine

import javafx.application.Platform

import comm.FileUtils
import comm.gui.AlertUtils
import comm.lang.Convert.funcToRunnable
import hk.edu.polyu.ir.groupc.searchengine.frontend.MainController

/**
  * Created by beenotung on 10/23/15.
  */
object Debug {
  lazy val skipDebug = false
  lazy val skipError = false
  lazy val FILE_LOG = "log.txt"
  lazy val skipProgress = false
  lazy val process_step = 5f / 100f

  def exception(e: Exception) = {
    e.printStackTrace()
    loge(e)
  }

  def loge(x: Any, detail: String = null) = {
    log("Error: " + x, mainStatus = true)
    if (detail != null) log(detail)
    try {
      if (detail == null)
        AlertUtils.error_(x.toString)
      else
        AlertUtils.error(headerText = x.toString, contentText = detail)
    } catch {
      case e: Exception =>
        log("GUI is not supported, error message not prompted")
    }
  }

  def loge(msg: String, e: Exception) = {
    log_(msg)
    log_(e)
    try {
      e.printStackTrace()
      AlertUtils.error(contentText = msg, exception = e)
    } catch {
      case e: Exception =>
        log("GUI is not supported, error message not prompted")
    }
  }

  def loge_(x: Any) = loge(x)

  def logBothStatus(main: String, minor: String) = {
    log_(main)
    log_(minor)
    MainController.bothStatus(main, minor)
  }

  def logDone(x: Any = {
    try {
      MainController getInstance() getMajorStatus
    } catch {
      case e: IllegalStateException => ""
    }
  }) = {
    log_("Done " + x)
    Platform runLater (() => MainController statusDone x.toString)
  }

  def log_(x: Any) = log(x, mainStatus = false, minorStatus = false)

  def log(x: Any, mainStatus: Boolean = false, minorStatus: Boolean = false) = {
    println(x)
    Console.out.flush()
    FileUtils.appendToFile(x.toString :: Nil, FILE_LOG)
    if (mainStatus) Platform runLater (() => MainController statusMain x.toString)
    if (minorStatus) Platform runLater (() => MainController statusMinor x.toString)
  }

  def logMainStatus(x: Any) = {
    log_(x)
    Platform runLater (() => {
      MainController statusMinor ""
      MainController statusMain x.toString
    })
  }

  def logp(x: Any) = {
    if (MainController.getInstance() != null) {
      Platform runLater (() => MainController statusMinor x.toString)
    }
    if (!skipProgress) {
      println(x)
      Console.out.flush()
    }
  }

  def logd(x: Any) = if (!skipDebug) log(x)
}
