package org.sakaiproject.tool.assessment.util;

import java.util.HashMap;

import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Actor;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Context;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Object;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Result;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Statement;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Verb;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Verb.SAKAI_VERB;
import org.sakaiproject.tool.assessment.data.dao.assessment.PublishedAssessmentData;
import org.sakaiproject.tool.assessment.data.dao.grading.AssessmentGradingData;
import org.sakaiproject.tool.assessment.data.ifc.assessment.PublishedAssessmentIfc;
import org.sakaiproject.tool.assessment.facade.PublishedAssessmentFacade;

/* 
 * Class that holds custom code for generating LRS_Statements that contain special samigo Metadata
 */
public class SamigoLRSStatements {
    private static final ServerConfigurationService serverConfigurationService = ComponentManager.get( ServerConfigurationService.class );

    public static LRS_Statement getStatementForTakeAssessment(String assessmentTitle, boolean pastDue, boolean isViaURL) {
    	StringBuffer lrssMetaInfo = new StringBuffer("Assesment: " + assessmentTitle);
    	lrssMetaInfo.append(", Past Due?: " + pastDue);
    	if (isViaURL) {
    		lrssMetaInfo.append(", Assesment taken via URL.");
    	}
    	
        String url = serverConfigurationService.getPortalUrl();
        LRS_Verb verb = new LRS_Verb(SAKAI_VERB.attempted);
        LRS_Object lrsObject = new LRS_Object(url + "/assessment", "attempted-assessment");
        HashMap<String, String> nameMap = new HashMap<String, String>();
        nameMap.put("en-US", "User attempted assessment");
        lrsObject.setActivityName(nameMap);
        HashMap<String, String> descMap = new HashMap<String, String>();
        descMap.put("en-US", "User attempted assessment: " + lrssMetaInfo);
        lrsObject.setDescription(descMap);
        return new LRS_Statement(null, verb, lrsObject);
    }
    
    public static LRS_Statement getStatementForGradedAssessment(AssessmentGradingData gradingData, PublishedAssessmentFacade publishedAssessment) {
        LRS_Verb verb = new LRS_Verb(SAKAI_VERB.scored);
        LRS_Object lrsObject = new LRS_Object(serverConfigurationService.getPortalUrl() + "/assessment", "received-grade-assessment");
        HashMap<String, String> nameMap = new HashMap<>();
        nameMap.put("en-US", "User received a grade");
        lrsObject.setActivityName(nameMap);
        HashMap<String, String> descMap = new HashMap<>();
        descMap.put("en-US", "User received a grade for their assessment: " + publishedAssessment.getTitle() + "; Submitted: "
                + (gradingData.getIsLate() ? "late" : "on time"));
        lrsObject.setDescription(descMap);
        LRS_Context context = new LRS_Context("other", "assessment");
        LRS_Statement statement = new LRS_Statement(null, verb, lrsObject, getLRS_Result(gradingData, publishedAssessment), context);
        return statement;
	}

    public static LRS_Statement getStatementForTotalScoreUpdate(AssessmentGradingData gradingData, PublishedAssessmentData publishedAssessment) {
        LRS_Verb verb = new LRS_Verb(SAKAI_VERB.scored);
        LRS_Object lrsObject = new LRS_Object(serverConfigurationService.getPortalUrl() + "/assessment", "total-score-update");
        HashMap<String, String> nameMap = new HashMap<>();
        nameMap.put("en-US", "Total score updated");
        lrsObject.setActivityName(nameMap);
        HashMap<String, String> descMap = new HashMap<>();
        descMap.put("en-US", "Total score updated for: " + publishedAssessment.getTitle() + "; Submitted: "
                + (gradingData.getIsLate() ? "late" : "on time"));
        lrsObject.setDescription(descMap);
        LRS_Context context = new LRS_Context("other", "assessment");
        LRS_Statement statement = new LRS_Statement(null, verb, lrsObject, getLRS_Result(gradingData, publishedAssessment), context);
        return statement;
    }

    public static LRS_Statement getStatementForStudentScoreUpdate(AssessmentGradingData gradingData, PublishedAssessmentData publishedAssessment) {
        LRS_Verb verb = new LRS_Verb(SAKAI_VERB.scored);
        LRS_Object lrsObject = new LRS_Object(serverConfigurationService.getPortalUrl() + "/assessment", "student-score-update");
        HashMap<String, String> nameMap = new HashMap<>();
        nameMap.put("en-US", "Student score updated");
        lrsObject.setActivityName(nameMap);
        HashMap<String, String> descMap = new HashMap<>();
        descMap.put("en-US", "Student score updated for: " + publishedAssessment.getTitle() + "; Submitted: "
                + (gradingData.getIsLate() ? "late" : "on time"));
        lrsObject.setDescription(descMap);
        LRS_Context context = new LRS_Context("other", "assessment");
        LRS_Statement statement = new LRS_Statement(null, verb, lrsObject, getLRS_Result(gradingData, publishedAssessment), context);
        return statement;
    }

    
    private static LRS_Result getLRS_Result(AssessmentGradingData gradingData, PublishedAssessmentIfc publishedAssessment) {
        double score = gradingData.getFinalScore();
        LRS_Result result = new LRS_Result(score, 0.0, publishedAssessment.getTotalScore(), null);
        result.setCompletion(true);
        return result;
    }
    
}
