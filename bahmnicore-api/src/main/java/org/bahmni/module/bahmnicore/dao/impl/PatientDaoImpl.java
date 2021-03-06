package org.bahmni.module.bahmnicore.dao.impl;

import org.apache.commons.lang.ArrayUtils;
import org.bahmni.module.bahmnicore.contract.patient.response.PatientResponse;
import org.bahmni.module.bahmnicore.dao.PatientDao;
import org.bahmni.module.bahmnicore.model.NameSearchParameter;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.SessionFactory;
import org.hibernate.classic.Session;
import org.hibernate.transform.Transformers;
import org.hibernate.type.StandardBasicTypes;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

@Repository
public class PatientDaoImpl implements PatientDao {
    private static final String PATIENT_IDENTIFIER_PARAM = "patientIdentifier";
    private static final String LIMIT_PARAM = "limit";
    private static final String OFFSET_PARAM = "offset";
    private static final String VILLAGE_PARAM = "village";
    private static final String LOCAL_NAME_PARAM = "localName";
    private static final String PERSON_ATTRIBUTE_NAMES_PARAMETER = "personAttributeTypeNames";
    private static final String PERSON_ATTRIBUTE_IDS_PARAMETER = "personAttributeTypeIds";

    public static final String WHERE_CLAUSE = " where p.voided = 'false' and pn.voided = 'false' and pn.preferred=true ";
    public static final String SELECT_STATEMENT = "select p.uuid as uuid, pi.identifier as identifier, pn.given_name as givenName, pn.middle_name as middleName, pn.family_name as familyName, p.gender as gender, p.birthdate as birthDate," +
            " p.death_date as deathDate, pa.city_village as cityVillage, p.date_created as dateCreated, v.uuid as activeVisitUuid ";
    public static final String FROM_TABLE = " from patient pat " ;
    public static final String JOIN_CLAUSE =  " inner join person p on pat.patient_id=p.person_id " +
            " left join person_name pn on pn.person_id = p.person_id" +
            " left join person_address pa on p.person_id=pa.person_id and pa.voided = 'false'" +
            " inner join patient_identifier pi on pi.patient_id = p.person_id " +
            " left outer join visit v on v.patient_id = pat.patient_id and v.date_stopped is null ";
    public static final String BY_ID = "pi.identifier = :" + PATIENT_IDENTIFIER_PARAM;
    public static final String BY_NAME_PARTS = " concat(coalesce(given_name, ''), coalesce(middle_name, ''), coalesce(family_name, '')) like ";
    public static final String BY_VILLAGE = " pa.city_village like :" + VILLAGE_PARAM;
    public static final String ORDER_BY = " order by p.date_created desc LIMIT :" + LIMIT_PARAM + " OFFSET :" + OFFSET_PARAM;

    private SessionFactory sessionFactory;

    @Autowired
    public PatientDaoImpl(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public List<PatientResponse> getPatients(String identifier, String name, String localName, String village, Integer length, Integer offset, String[] patientAttributes) {
        Session currentSession = sessionFactory.getCurrentSession();
        NameSearchParameter nameSearchParameter = NameSearchParameter.create(name);
        String nameSearchCondition = getNameSearchCondition(nameSearchParameter);
        NameSearchParameter localNameParameters = NameSearchParameter.create(localName);
        String localNameJoins = getLocalNameJoins(localNameParameters, patientAttributes);
        String selectStatement = getSelectStatementWithLocalName(SELECT_STATEMENT, patientAttributes);

        String group_by = " group by p.person_id, p.uuid , pi.identifier , pn.given_name , pn.middle_name , pn.family_name , \n" +
                "p.gender , p.birthdate , p.death_date , pa.city_village , p.date_created , \n" +
                "v.uuid  ";
        String query = selectStatement + FROM_TABLE + JOIN_CLAUSE + localNameJoins + WHERE_CLAUSE;

        query = isEmpty(identifier) ? query : combine(query, "and", enclose(BY_ID));
        query = isEmpty(nameSearchCondition) ? query : combine(query, "and", enclose(nameSearchCondition));
        query = isEmpty(village) ? query : combine(query, "and", enclose(BY_VILLAGE));

        if(patientAttributes !=null && patientAttributes.length >0) {
            query += group_by;
        }
        query += ORDER_BY;
        SQLQuery sqlQuery = currentSession
                .createSQLQuery(query)
                .addScalar("uuid", StandardBasicTypes.STRING)
                .addScalar("identifier", StandardBasicTypes.STRING)
                .addScalar("givenName", StandardBasicTypes.STRING)
                .addScalar("middleName", StandardBasicTypes.STRING)
                .addScalar("familyName", StandardBasicTypes.STRING)
                .addScalar("gender", StandardBasicTypes.STRING)
                .addScalar("birthDate", StandardBasicTypes.DATE)
                .addScalar("deathDate", StandardBasicTypes.DATE)
                .addScalar("cityVillage", StandardBasicTypes.STRING)
                .addScalar("dateCreated", StandardBasicTypes.TIMESTAMP)
                .addScalar("activeVisitUuid", StandardBasicTypes.STRING);

        if (isNotEmpty(identifier))
            sqlQuery.setParameter(PATIENT_IDENTIFIER_PARAM, identifier);
        if (isNotEmpty(village))
            sqlQuery.setParameter(VILLAGE_PARAM, village + "%");
        if(patientAttributes !=null && patientAttributes.length >0){
            sqlQuery.addScalar("localName", StandardBasicTypes.STRING);
            sqlQuery.setParameterList(PERSON_ATTRIBUTE_NAMES_PARAMETER, Arrays.asList(patientAttributes));
        }
        if(!localNameParameters.isEmpty()) {
            sqlQuery = replacePatientAttributeTypeParameters(patientAttributes, sqlQuery, currentSession);
        }
        replaceLocalNamePartParameters(localNameParameters, sqlQuery);
        sqlQuery.setParameter(LIMIT_PARAM, length);
        sqlQuery.setParameter(OFFSET_PARAM, offset);
        sqlQuery.setResultTransformer(Transformers.aliasToBean(PatientResponse.class));
        return sqlQuery.list();
    }

    private String getSelectStatementWithLocalName(String selectStatement, String[] patientAttributes){
        if(patientAttributes!= null && patientAttributes.length > 0){
            return selectStatement + " ,group_concat(distinct(coalesce(concat(attrt.name, ':', pattrln.value))) SEPARATOR ' ') as localName ";
        }
        return selectStatement;
    }

    private SQLQuery replacePatientAttributeTypeParameters(String[] patientAttributes, SQLQuery sqlQuery, Session currentSession) {
        if (!ArrayUtils.isEmpty(patientAttributes)) {
            ArrayList<Integer> personAttributeIds = getPersonAttributeIds(patientAttributes, currentSession);
            sqlQuery.setParameterList(PERSON_ATTRIBUTE_IDS_PARAMETER, personAttributeIds);
        }
        return sqlQuery;
    }

    private ArrayList<Integer> getPersonAttributeIds(String[] patientAttributes, Session currentSession) {
        String query = "select person_attribute_type_id from person_attribute_type where name in " +
                "( :" + PERSON_ATTRIBUTE_NAMES_PARAMETER + " )";
        Query queryToGetAttributeIds = currentSession.createSQLQuery(query);
        queryToGetAttributeIds.setParameterList(PERSON_ATTRIBUTE_NAMES_PARAMETER, Arrays.asList(patientAttributes));
        List list = queryToGetAttributeIds.list();
        return (ArrayList<Integer>) list;
    }


    private SQLQuery replaceLocalNamePartParameters(NameSearchParameter localNameParameters, SQLQuery sqlQuery) {
        int index = 0;
        for (String localNamePart : localNameParameters.getNameParts()) {
            sqlQuery.setParameter(LOCAL_NAME_PARAM + index++, localNamePart);
        }
        return sqlQuery;
    }

    private String getLocalNameJoins(NameSearchParameter localNameParameters, String[] patientAttributes) {
        String localNameGetJoin = " left outer join person_attribute pattrln on pattrln.person_id = p.person_id " +
                " left outer join person_attribute_type attrt on attrt.person_attribute_type_id = pattrln.person_attribute_type_id and attrt.name in (:" + PERSON_ATTRIBUTE_NAMES_PARAMETER + ") ";
        String joinStatement = "";

        if (!localNameParameters.isEmpty()) {
            for (int index = 0; index < localNameParameters.getNameParts().length; index++) {
                String indexString = String.valueOf(index);
                joinStatement = joinStatement +
                        " inner join person_attribute pattr" + indexString +
                        " on pattr" + indexString + ".person_id=p.person_id" +
                        " and pattr" + indexString + ".value like :" + LOCAL_NAME_PARAM + indexString;
                if (patientAttributes != null && patientAttributes.length > 0) {
                    joinStatement = joinStatement + " and pattr" + indexString + ".person_attribute_type_id in ( :" + PERSON_ATTRIBUTE_IDS_PARAMETER + " )";
                }
            }
        }
        if (patientAttributes != null && patientAttributes.length > 0) {
            joinStatement = joinStatement + localNameGetJoin;
        }
        return joinStatement;
    }

    @Override
    public Patient getPatient(String identifier) {
        Session currentSession = sessionFactory.getCurrentSession();
        List<PatientIdentifier> ident = currentSession.createQuery("from PatientIdentifier where identifier = :ident").setString("ident", identifier).list();
        if (!ident.isEmpty()) {
            return ident.get(0).getPatient();
        }
        return null;
    }

    @Override
    public List<Patient> getPatients(String partialIdentifier, boolean shouldMatchExactPatientId) {
        if (!shouldMatchExactPatientId) {
            partialIdentifier = "%" + partialIdentifier;
            Query querytoGetPatients = sessionFactory.getCurrentSession().createQuery(
                    "select pi.patient " +
                            " from PatientIdentifier pi " +
                            " where pi.identifier like :partialIdentifier ");
            querytoGetPatients.setString("partialIdentifier", partialIdentifier);
            return querytoGetPatients.list();
        }

        Query querytoGetPatients = sessionFactory.getCurrentSession().createQuery(
                "select pi.patient " +
                        " from PatientIdentifier pi " +
                        " where pi.identifier = :partialIdentifier ");
        querytoGetPatients.setString("partialIdentifier", partialIdentifier);
        return querytoGetPatients.list();
    }

    private String getNameSearchCondition(NameSearchParameter nameSearchParameter) {
        if (nameSearchParameter.isEmpty())
            return "";
        else {
            String query_by_name_parts = "";
            for (String part : nameSearchParameter.getNameParts()) {
                if (!query_by_name_parts.equals("")) {
                    query_by_name_parts += " and " + BY_NAME_PARTS + " '" + part + "'";
                } else {
                    query_by_name_parts += BY_NAME_PARTS + " '" + part + "'";
                }
            }
            return query_by_name_parts;
        }
    }

    private static String combine(String query, String operator, String condition) {
        return String.format("%s %s %s", query, operator, condition);
    }

    private static String enclose(String value) {
        return String.format("(%s)", value);
    }
}
