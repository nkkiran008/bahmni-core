package org.openmrs.module.bahmniemrapi.encountertransaction.mapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Person;
import org.openmrs.Visit;
import org.openmrs.module.bahmniemrapi.builder.ConceptBuilder;
import org.openmrs.module.bahmniemrapi.builder.EncounterBuilder;
import org.openmrs.module.bahmniemrapi.builder.ObsBuilder;
import org.openmrs.module.bahmniemrapi.builder.PersonBuilder;
import org.openmrs.module.bahmniemrapi.builder.VisitBuilder;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.emrapi.encounter.matcher.ObservationTypeMatcher;
import org.openmrs.test.TestUtil;
import org.openmrs.util.LocaleUtility;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;


@RunWith(PowerMockRunner.class)
@PrepareForTest(LocaleUtility.class)
public class OMRSObsToBahmniObsMapperTest {

    @Mock
    private ObservationTypeMatcher observationTypeMatcher;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        mockStatic(LocaleUtility.class);
        when(LocaleUtility.getDefaultLocale()).thenReturn(Locale.ENGLISH);
        when(observationTypeMatcher.getObservationType(any(Obs.class))).thenReturn(ObservationTypeMatcher.ObservationType.OBSERVATION);
    }

    @Test
    public void return_mapped_observations_for_abnormal_observation_structure() throws Exception {
        Date date = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).parse("January 2, 2010");
        Person person = new PersonBuilder().withUUID("puuid").build();
        Visit visit = new VisitBuilder().withPerson(person).withUUID("vuuid").withStartDatetime(date).build();
        Encounter encounter = new EncounterBuilder().withVisit(visit).withPatient(person).withUUID("euuid").withDatetime(date).build();

        Concept conceptDetailsConceptSet = new ConceptBuilder().withName("conceptDetailsConceptSet").withDataType("cdatatype", "hl7abbrev").withUUID("cuuid").withClass(ETObsToBahmniObsMapper.CONCEPT_DETAILS_CONCEPT_CLASS).build();
        Concept abnormalConcept = new ConceptBuilder().withName("abnormalConcept").withCodedDataType().withUUID("cuuid1").withClass(ETObsToBahmniObsMapper.ABNORMAL_CONCEPT_CLASS).build();
        Concept durationConcept = new ConceptBuilder().withName("durationConcept").withDataTypeNumeric().withUUID("cuuid2").withClass(ETObsToBahmniObsMapper.DURATION_CONCEPT_CLASS).build();
        Concept trueConcept = new ConceptBuilder().withName("True").withDataType("cdatatype", "hl7abbrev").withUUID("cuuid11").withClass("").build();
        Concept valueConcept = new ConceptBuilder().withName("valueConcept").withDataType("cdatatype", "hl7abbrev").withUUID("cuuid2").withClass("").build();
        conceptDetailsConceptSet.addSetMember(abnormalConcept);
        conceptDetailsConceptSet.addSetMember(durationConcept);
        conceptDetailsConceptSet.addSetMember(valueConcept);

        Concept valueConcept2 = new ConceptBuilder().withName("valueConcept2").withDataType("cdatatype", "hl7abbrev").withUUID("cuuid2").withClass("").build();

        Concept parentConcept = new ConceptBuilder().withName("parentConcept").withDataType("N/A").build();
        parentConcept.addSetMember(conceptDetailsConceptSet);
        parentConcept.addSetMember(valueConcept2);

        Obs abnormalObs = new ObsBuilder().withPerson(person).withEncounter(encounter).withConcept(abnormalConcept).withValue(trueConcept).withDatetime(date).build();
        Obs durationObs = new ObsBuilder().withPerson(person).withEncounter(encounter).withConcept(durationConcept).withValue(10.0).withDatetime(date).build();
        Obs valueObs = new ObsBuilder().withPerson(person).withEncounter(encounter).withConcept(valueConcept).withValue("ovalue").withDatetime(date).build();
        Obs obs1 = new ObsBuilder().withConcept(conceptDetailsConceptSet).withGroupMembers(valueObs, abnormalObs, durationObs).build();
        Obs obs2 = new ObsBuilder().withConcept(valueConcept2).withValue("ovalue2").build();
        Obs parentObs = new ObsBuilder().withPerson(person).withEncounter(encounter).withConcept(parentConcept).withDatetime(date).withGroupMembers(obs1, obs2).build();

        Collection<BahmniObservation> parentsObservations = new OMRSObsToBahmniObsMapper(new ETObsToBahmniObsMapper(null), observationTypeMatcher).map(asList(parentObs), Arrays.asList(parentConcept));
        assertEquals(1, parentsObservations.size());
        BahmniObservation parentObservation = parentsObservations.iterator().next();
        assertEquals("parentConcept", parentObservation.getConcept().getName());
        Collection<BahmniObservation> childObservations = parentObservation.getGroupMembers();
        assertEquals(2, childObservations.size());
        assertEquals(1, parentObservation.getConceptSortWeight().intValue());

        BahmniObservation childObservation1 = getObservation(obs1.getUuid(), childObservations);
        assertEquals("ovalue", childObservation1.getValue());
        assertEquals("cdatatype", childObservation1.getType());
        assertEquals(2, childObservation1.getConceptSortWeight().intValue());
        assertTrue(childObservation1.isAbnormal());
        assertEquals(10L, childObservation1.getDuration().longValue());

        BahmniObservation childObservation2 = getObservation(obs2.getUuid(), childObservations);
        assertEquals("ovalue2", childObservation2.getValue());
        assertEquals("cdatatype", childObservation2.getType());
        assertEquals(6, childObservation2.getConceptSortWeight().intValue());
        assertNull(childObservation2.isAbnormal());
        assertNull(childObservation2.getDuration());
        assertEquals(TestUtil.createDateTime("2010-01-02"), childObservation1.getVisitStartDateTime());
        assertEquals(TestUtil.createDateTime("2010-01-02"), childObservation2.getVisitStartDateTime());
        assertEquals(TestUtil.createDateTime("2010-01-02"), parentObservation.getVisitStartDateTime());
    }

    private BahmniObservation getObservation(String uuid, Collection<BahmniObservation> childObservations) {
        for (BahmniObservation o : childObservations) {
            if (o.getUuid().equals(uuid)) {
                return o;
            }
        }
        return null;
    }

}
