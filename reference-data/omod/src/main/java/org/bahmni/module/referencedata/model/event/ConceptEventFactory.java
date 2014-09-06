package org.bahmni.module.referencedata.model.event;

public class ConceptEventFactory {
    static final String CONCEPT_URL = "/openmrs/ws/rest/v1/reference-data/%s/%s";
    public static final String LAB = "lab";
    public static final String SAMPLE = "sample";
    private static final String DEPARTMENT = "department";

    public static ConceptOperationEvent sampleEvent() {
        return new SampleEvent(CONCEPT_URL, LAB, SAMPLE);
    }

    public static ConceptOperationEvent labConceptSetEvent() {
        return new LabConceptSetEvent();
    }

    public static ConceptOperationEvent departmentEvent() {
        return new DepartmentEvent(CONCEPT_URL, LAB, DEPARTMENT);
    }
}