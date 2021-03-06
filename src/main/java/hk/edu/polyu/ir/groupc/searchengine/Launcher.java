package hk.edu.polyu.ir.groupc.searchengine;

import comm.exception.EssentialFileNotFoundException;
import comm.exception.RichFileNotFoundException;
import comm.gui.AlertUtils;
import comm.lang.ScalaSupport;
import hk.edu.polyu.ir.groupc.searchengine.frontend.MainController;
import hk.edu.polyu.ir.groupc.searchengine.model.datasource.DocFileFactory;
import hk.edu.polyu.ir.groupc.searchengine.model.datasource.StopWordFactory;
import hk.edu.polyu.ir.groupc.searchengine.model.datasource.TermIndexFactory;
import hk.edu.polyu.ir.groupc.searchengine.model.query.Query;
import hk.edu.polyu.ir.groupc.searchengine.model.query.QueryEnum;
import hk.edu.polyu.ir.groupc.searchengine.model.query.QueryFactory;
import hk.edu.polyu.ir.groupc.searchengine.model.result.JudgeRobustFactory;
import hk.edu.polyu.ir.groupc.searchengine.model.result.RetrievalDocument;
import hk.edu.polyu.ir.groupc.searchengine.model.result.SearchResult;
import hk.edu.polyu.ir.groupc.searchengine.model.result.SearchResultFactory;
import hk.edu.polyu.ir.groupc.searchengine.model.retrievalmodel.RetrievalModel;
import scala.collection.SeqView;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static hk.edu.polyu.ir.groupc.searchengine.Debug.*;

public abstract class Launcher {
    private boolean inited = false;
    private String _filePath, _termIndexPath, _postPath, _stopPath, _judgeRobust, _queryPath;

    public String filePath() {
        return _filePath;
    }

    public void filePath(String newPath) throws RichFileNotFoundException {
        _filePath = newPath;
        if (inited) DocFileFactory.load(new File(newPath));
    }

    public void termIndexPath(String newPath) throws RichFileNotFoundException {
        _termIndexPath = newPath;
        if (inited) TermIndexFactory.load(new File(newPath));
    }

    public String termIndexPath() {
        return _termIndexPath;
    }

    public void postPath(String newPath) throws RichFileNotFoundException {
        _postPath = newPath;
        if (inited) TermIndexFactory.build(new File(newPath));
    }

    public String postPath() {
        return _postPath;
    }

    public void stopPath(String newPath) throws RichFileNotFoundException {
        _stopPath = newPath;
        if (inited) StopWordFactory.load(new File(newPath));
    }

    public String stopPath() {
        return _stopPath;
    }

    public void judgeRobustPath(String newPath) throws RichFileNotFoundException {
        _judgeRobust = newPath;
        if (inited) JudgeRobustFactory.load(new File(newPath));
    }

    public String judgeRobustPath() {
        return _judgeRobust;
    }

    @Deprecated
    public void queryPath_T(String newPath) throws RichFileNotFoundException {
        _queryPath = newPath;
        if (inited) QueryFactory.loadFromFile(new File(newPath), QueryEnum.T().id());
    }

    @Deprecated
    public void queryPath_TDN(String newPath) throws RichFileNotFoundException {
        _queryPath = newPath;
        if (inited) QueryFactory.loadFromFile(new File(newPath), QueryEnum.TDN().id());
    }

//    @Deprecated
//    public String queryPath() {
//        return _queryPath;
//    }

    public boolean needDocumentIndex() {
        return true;
    }

    public boolean test(RetrievalModel retrievalModel, int[] numOfRetrievalDocuments, int queryType) throws EssentialFileNotFoundException {
        boolean noError = false;
        init();
        logMainStatus("running retrieval model: " + retrievalModel.getClass().getName());
        for (int numOfRetrievalDocument : numOfRetrievalDocuments) {
            String resultFilename = numOfRetrievalDocument + ".ret";
            start(retrievalModel, resultFilename, numOfRetrievalDocument, queryType);
        }
//        int[] numDocs = new int[]{5, 10, 15, 20, 30,
//                100, 200, 500,
//                1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900,
//                2000, 2500, 3000, 4000, 5000, 10000};
        return true;
    }

    /**
     * @return time used in nanosecond
     */
    public long start(RetrievalModel retrievalModel, String resultFilename, int numOfRetrievalDocument, int queryType) throws EssentialFileNotFoundException {
        init();
        logMainStatus("running retrieval model: " + retrievalModel.getClass().getName());
        final long startTime = System.nanoTime();
        List<SearchResult> searchResults = run(retrievalModel, numOfRetrievalDocument, queryType);
        final long endTime = System.nanoTime();
        try {
            SearchResultFactory.writeToFile(searchResults, resultFilename);
        } catch (IOException e) {
            loge_("Failed to save search result!\nPlease make sure you have write permission on '" + resultFilename + "'");
        }
        return endTime-startTime;
    }

    public void init() throws EssentialFileNotFoundException {
        if (inited) return;
        inited = true;
        try {
            logMainStatus("loading file-doc list");
            DocFileFactory.load(new File(filePath()));
            logDone("loaded file-doc list");
            try {
                logMainStatus("loading term index");
                TermIndexFactory.load(new File(termIndexPath()));
                logDone("loaded term index");
            } catch (comm.exception.RichFileNotFoundException e) {
                logMainStatus("term index not found\nbuilding term index");
                TermIndexFactory.build(new File(postPath()));
//                logDone("built index, saving term index");
                logMainStatus("built index, saving term index");
                TermIndexFactory.getTermIndex().writeToFile(termIndexPath());
                logDone("saved term index");
            }
//            if (needDocumentIndex()) {
            logMainStatus("loading document index");
            TermIndexFactory.getTermIndex().createDocumentIndex();
            logDone("loaded document index");
//            }
            logMainStatus("calculating IDF");
            TermIndexFactory.getTermIndex().cacheIDF();
            logDone("calculated IDF");
            logMainStatus("loading stop word list");
            StopWordFactory.load(new File(stopPath()));
            logDone("loaded stop word list");
        } catch (RichFileNotFoundException e) {
            throw new EssentialFileNotFoundException(e.path);
        }
    }

    public void loadQuery_T(File file) throws RichFileNotFoundException {
        logMainStatus("loading query_T");
        QueryFactory.loadFromFile(file, QueryEnum.T().id());
        logDone("loaded query_T");
    }

    public void loadQuery_TDN(File file) throws RichFileNotFoundException {
        logMainStatus("loading query_TDN");
        QueryFactory.loadFromFile(file, QueryEnum.TDN().id());
        logDone("loaded query_TDN");
    }

    private List<SearchResult> run(RetrievalModel retrievalModel, int numOfRetrievalDocument, int queryType) {
        List<SearchResult> searchResults = new LinkedList<>();
        final String runId = MainController.newRunId(Config.groupName, retrievalModel);
        SeqView<Query, scala.collection.immutable.List<Query>> queries;
        if (queryType == QueryEnum.TDN().id())
            queries = QueryFactory.getQueries_TDN();
        else
            queries = QueryFactory.getQueries_T();
        queries.foreach(ScalaSupport.function1(new Function<Query, Object>() {
            @Override
            public Object apply(Query query) {
                logMainStatus("searching on queryId: " + query.queryId());
                List<RetrievalDocument> retrievalDocuments = retrievalModel.search(query);
                SearchResult searchResult = SearchResultFactory.create(runId, query.queryId(), retrievalDocuments);
                searchResult.shrink(numOfRetrievalDocument);
                searchResults.add(searchResult);
                logDone("finished search on queryId: " + query.queryId());
                return null;
            }
        }));
        return searchResults;
    }

    @Deprecated
    private void deInit() {

    }
}
