////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2014 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.webapp.controller;

import com.denimgroup.threadfix.data.entities.DefectTracker;
import com.denimgroup.threadfix.data.entities.DefectTrackerType;
import com.denimgroup.threadfix.remote.response.RestResponse;
import com.denimgroup.threadfix.service.ApplicationService;
import com.denimgroup.threadfix.service.DefectTrackerService;
import com.denimgroup.threadfix.service.PermissionService;
import com.denimgroup.threadfix.service.defects.AbstractDefectTracker;
import com.denimgroup.threadfix.webapp.validator.BeanValidator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/configuration/defecttrackers/new")
@SessionAttributes("defectTracker")
@PreAuthorize("hasRole('ROLE_CAN_MANAGE_DEFECT_TRACKERS')")
public class AddDefectTrackerController {

    @Autowired
	private DefectTrackerService defectTrackerService;
    @Autowired
	private ApplicationService applicationService;
    @Autowired(required = false)
    @Nullable
	private PermissionService permissionService;
	
	public AddDefectTrackerController(){}
	
	private final Log log = LogFactory.getLog(AddDefectTrackerController.class);

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.setValidator(new BeanValidator());
	}
	
	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setAllowedFields("name", "url", "defectTrackerType.id");
	}

	@ModelAttribute
	public List<DefectTrackerType> populateDefectTrackerTypes() {
		return defectTrackerService.loadAllDefectTrackerTypes();
	}

	@RequestMapping(method = RequestMethod.GET)
	public String setup(Model model) {
		DefectTracker defectTracker = new DefectTracker();
		model.addAttribute(defectTracker);
		return "config/defecttrackers/form";
	}

	@RequestMapping(method = RequestMethod.POST)
	public @ResponseBody RestResponse<DefectTracker> processSubmit(@Valid @ModelAttribute DefectTracker defectTracker,
			BindingResult result, SessionStatus status, Model model,
			HttpServletRequest request) {
		if (defectTracker.getName().trim().equals("") && !result.hasFieldErrors("name")) {
			result.rejectValue("name", null, null, "This field cannot be blank");
		}
		
		if (result.hasErrors()) {
			model.addAttribute("contentPage", "config/defecttrackers/forms/createDTForm.jsp");
			return RestResponse.failure("Found some errors."); // TODO enhance the RestResponse class with those errors
		} else {
			
			DefectTracker databaseDefectTracker = defectTrackerService.loadDefectTracker(defectTracker.getName().trim());
			if (databaseDefectTracker != null)
				result.rejectValue("name", "errors.nameTaken");

			if (defectTracker.getDefectTrackerType() == null) {
				result.rejectValue("defectTrackerType.id", "errors.invalid", 
						new String [] { "Defect Tracker Type" }, null );
			
			} else if (defectTrackerService.loadDefectTrackerType(defectTracker.getDefectTrackerType().getId()) == null) {
				result.rejectValue("defectTrackerType.id", "errors.invalid", 
						new String [] { defectTracker.getDefectTrackerType().getId().toString() }, null );
			} else if (!defectTrackerService.checkUrl(defectTracker, result)) {
				if (!result.hasFieldErrors("url")) {
					result.rejectValue("url", "errors.invalid", new String [] { "URL" }, null);		
				} else if (result.getFieldError("url").getDefaultMessage() != null &&
						result.getFieldError("url").getDefaultMessage().equals(
								AbstractDefectTracker.INVALID_CERTIFICATE)){
					model.addAttribute("showKeytoolLink", true);
				}
			}
			
			if (result.hasErrors()) {
				model.addAttribute("contentPage", "config/defecttrackers/forms/createDTForm.jsp");
                return RestResponse.failure("Found some errors."); // TODO enhance the RestResponse class with those errors
			}
			
			defectTrackerService.storeDefectTracker(defectTracker);
			
			String user = SecurityContextHolder.getContext().getAuthentication().getName();
			log.debug(user + " has successfully created a Defect Tracker with the name " + defectTracker.getName() +
					", the URL " + defectTracker.getUrl() + 
					", the type " + defectTracker.getDefectTrackerType().getName() + 
					", and the ID " + defectTracker.getId());


            return RestResponse.success(defectTracker);
        }
	}
}
