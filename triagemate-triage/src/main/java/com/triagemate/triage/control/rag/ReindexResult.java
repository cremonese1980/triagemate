package com.triagemate.triage.control.rag;

public record ReindexResult(String model, int created, int skipped, int failed) {

    public int total() {
        return created + skipped + failed;
    }

    public boolean hasFailures() {
        return failed > 0;
    }
}
