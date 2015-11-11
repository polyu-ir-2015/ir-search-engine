package hk.edu.polyu.ir.groupc.searchengine.model.query;

import hk.edu.polyu.ir.groupc.searchengine.model.Index;

/**
 * Created by beenotung on 10/30/15.
 */
public class RetrievalDocument{
    public String documentId;
    /*higher is better*/
    public double similarityScore;

    public RetrievalDocument(int fileId, double similarityScore) {
        this.documentId = Index.getDocFile(fileId).docId();
        this.similarityScore = similarityScore;
    }
}