package org.qortal.controller.arbitrary;

public class ArbitraryDataExamination {

    private boolean pass;

    private String notes;

    public ArbitraryDataExamination(boolean pass, String notes) {
        this.pass = pass;
        this.notes = notes;
    }

    public boolean isPass() {
        return pass;
    }

    public String getNotes() {
        return notes;
    }
}
