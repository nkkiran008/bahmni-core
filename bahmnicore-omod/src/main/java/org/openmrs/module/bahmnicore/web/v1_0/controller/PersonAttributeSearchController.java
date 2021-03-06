package org.openmrs.module.bahmnicore.web.v1_0.controller;

import org.openmrs.module.webservices.rest.web.annotation.WSDoc;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.bahmni.module.bahmnicore.dao.PersonAttributeDao;
import org.bahmni.module.bahmnicore.model.ResultList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/rest/v1/bahmnicore/unique/personattribute")
public class PersonAttributeSearchController extends BaseRestController {
	
	private PersonAttributeDao personAttributeDao;
	
	@Autowired
	public PersonAttributeSearchController(PersonAttributeDao personAttributeDao) {
		this.personAttributeDao = personAttributeDao;
	}
	
	@RequestMapping(method = RequestMethod.GET, params = { "q", "key" })
	@WSDoc("Get unique values for a person attribute")
	public ResultList search(@RequestParam String key, @RequestParam String q) {
		return personAttributeDao.getUnique(key, q);
	}
}
