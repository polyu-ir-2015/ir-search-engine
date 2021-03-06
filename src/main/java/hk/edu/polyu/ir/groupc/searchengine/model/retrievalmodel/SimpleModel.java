package hk.edu.polyu.ir.groupc.searchengine.model.retrievalmodel;

import comm.lang.ScalaSupport;
import hk.edu.polyu.ir.groupc.searchengine.model.Index;
import hk.edu.polyu.ir.groupc.searchengine.model.query.ExpandedTerm;
import hk.edu.polyu.ir.groupc.searchengine.model.query.Query;
import hk.edu.polyu.ir.groupc.searchengine.model.result.RetrievalDocument;
import scala.Tuple2;
import scala.collection.mutable.ArrayBuffer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by beenotung on 11/12/15.
 */
public class SimpleModel extends RetrievalModel {
    private static final String MODE_DEFAULT = "Default";
    private final List<Parameter<? extends Number>> PARAMETERS;
    private final List<String> MODES;
    private String mode;

    {
        MODES = new LinkedList<>();
        MODES.add(MODE_DEFAULT);
        MODES.add("extra mode 1");
        MODES.add("extra mode 2");
        MODES.add("extra mode 3");
        PARAMETERS = new LinkedList<>();
        PARAMETERS.add(new DoubleParameter("P1", 0d, 1d, 0.5d));
        PARAMETERS.add(new IntegerParameter("P2", 5, 10, 8));
    }

    @Override
    public String name() {
        return "Simple Model (Demo)";
    }

    @Override
    public List<String> getModes() {
        return MODES;
    }

    @Override
    public String getDefaultMode() {
        return MODE_DEFAULT;
    }

    @Override
    public String getMode() {
        return mode;
    }

    @Override
    public void setMode(String newMode) {
        mode = newMode;
    }

    @Override
    public List<Parameter<? extends Number>> getParameters() {
        return PARAMETERS;
    }

    @Override
    public List<RetrievalDocument> search(Query query) {
        ArrayList<RetrievalDocument> retrievalDocuments = new ArrayList<>();
        for (ExpandedTerm expandedTerm : query.expandedTerms()) {
            ScalaSupport.foreachMap(expandedTerm.term().filePositionMap(), new Consumer<Tuple2<Object, ArrayBuffer<Object>>>() {
                @Override
                public void accept(Tuple2<Object, ArrayBuffer<Object>> e) {
                    double similarityScore = e._2().size() * expandedTerm.weight();
                    similarityScore /= Index.getDocumentLength((Integer) e._1());
                    retrievalDocuments.add(new RetrievalDocument((int) e._1(), similarityScore));
                }
            });
        }
        return retrievalDocuments;
    }
}
