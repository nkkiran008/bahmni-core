package org.bahmni.module.bahmnicore.service;

import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.Visit;

import java.util.List;

public interface OrderService {
    List<Order> getPendingOrders(String patientUuid, String orderTypeUuid);

    List<Visit> getVisitsWithOrders(Patient patient, String orderType, Boolean includeActiveVisit, Integer numberOfVisits);
}
