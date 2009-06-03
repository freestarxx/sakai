package org.sakaiproject.profile2.tool.pages;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.profile2.api.ProfileConstants;
import org.sakaiproject.profile2.api.exception.ProfilePrototypeNotDefinedException;
import org.sakaiproject.profile2.api.model.ProfileStatus;
import org.sakaiproject.profile2.tool.components.ProfileImageRenderer;
import org.sakaiproject.profile2.tool.models.FriendAction;
import org.sakaiproject.profile2.tool.pages.panels.FriendsFeed;
import org.sakaiproject.profile2.tool.pages.windows.AddFriend;
import org.sakaiproject.profile2.util.ProfileUtils;


public class ViewProfile extends BasePage {

	private static final Logger log = Logger.getLogger(ViewProfile.class);
    private transient ProfileStatus profileStatus;
	
	public ViewProfile(String userUuid)   {
		
		log.debug("ViewProfile()");

		//setup model to store the actions in the modal windows
		final FriendAction friendActionModel = new FriendAction();
		
		//get current user Id
		String currentUserId = sakaiProxy.getCurrentUserId();
				
		//double check, if somehow got to own ViewPage, redirect to MyProfile
		if(userUuid.equals(currentUserId)) {
			log.warn("ViewProfile: user " + userUuid + " accessed ViewProfile for self. Redirecting...");
			throw new RestartResponseException(new MyProfile());
		}
		
		//post view event
		sakaiProxy.postEvent(ProfileConstants.EVENT_PROFILE_VIEW_OTHER, "/profile/"+userUuid, false);
		
		//init
		boolean friend = false;
		boolean friendRequestToThisPerson = false;
		boolean friendRequestFromThisPerson = false;

		//friend?
		friend = profile.isUserXFriendOfUserY(userUuid, currentUserId);

		//if not friend, has a friend request already been made to this person?
		if(!friend) {
			friendRequestToThisPerson = profile.isFriendRequestPending(currentUserId, userUuid);
		}
		
		//if not friend and no friend request to this person, has a friend request been made from this person to the current user?
		if(!friend && !friendRequestToThisPerson) {
			friendRequestFromThisPerson = profile.isFriendRequestPending(userUuid, currentUserId);
		}
		
		
		//is this user allowed to view this person's profile image?
		boolean isProfileImageAllowed = profile.isUserXProfileImageVisibleByUserY(userUuid, currentUserId, friend);
		
		//is this user allowed to view this person's status?
		boolean isStatusAllowed = profile.isUserXStatusVisibleByUserY(userUuid, currentUserId, friend);
		
		
		/* DEPRECATED via PRFL-24 when privacy was relaxed
		if(!isProfileAllowed) {
			throw new ProfileIllegalAccessException("User: " + currentUserId + " is not allowed to view profile for: " + userUuid);
		}
		*/
		
		//holds number of profile containers that are visible
		int visibleContainerCount = 0;
		
		//get SakaiPerson for the person who's profile we are viewing
		SakaiPerson sakaiPerson = sakaiProxy.getSakaiPerson(userUuid);
		//if null, they have no profile so just get a prototype
		if(sakaiPerson == null) {
			log.info("No SakaiPerson for " + userUuid);
			sakaiPerson = sakaiProxy.getSakaiPersonPrototype();
			//if its still null, throw exception
			if(sakaiPerson == null) {
				throw new ProfilePrototypeNotDefinedException("Couldn't create a SakaiPerson prototype for " + userUuid);
			}
		} 
		
		//get some values from SakaiPerson or SakaiProxy if empty
		//SakaiPerson returns NULL strings if value is not set, not blank ones
		String userDisplayName = sakaiProxy.getUserDisplayName(userUuid);
		
		/* IMAGE */
		add(new ProfileImageRenderer("photo", userUuid, isProfileImageAllowed, ProfileConstants.PROFILE_IMAGE_MAIN, true));
		
		/*STATUS PANEL */
		//container
		WebMarkupContainer statusContainer = new WebMarkupContainer("statusContainer");
		statusContainer.setOutputMarkupId(true);
		
		//get status
		profileStatus = profile.getUserStatus(userUuid);
		
		//if no status, initialise
		if(profileStatus == null) {
			statusContainer.setVisible(false); //hide status section
			profileStatus = new ProfileStatus();
		}
		
		//get the message
		String statusMessage = profileStatus.getMessage();
		if(StringUtils.isBlank(statusMessage)){
			statusContainer.setVisible(false);
			statusMessage = "";
		} 
		
		//get the formatted date
		Date statusDate = profileStatus.getDateAdded();
		String statusDateStr = "";
		if(statusDate == null) {
			statusContainer.setVisible(false);
		} else {
			//transform the date
			statusDateStr = ProfileUtils.convertDateForStatus(statusDate);
		}
		
		//name
		Label profileName = new Label("profileName", userDisplayName);
		add(profileName);
		
		
		//status
		Label statusMessageLabel = new Label("statusMessage", statusMessage);
		statusMessageLabel.setOutputMarkupId(true);
		statusContainer.add(statusMessageLabel);
		
		//status last updated
		Label statusDateLabel = new Label("statusDate", statusDateStr);
		statusDateLabel.setOutputMarkupId(true);
		statusContainer.add(statusDateLabel);
		
		//if this person is not allowed to view their status, hide it
		//TODO move this into a separet method and don't even get the data if they can't view it.
		if(!isStatusAllowed) {
			statusContainer.setVisible(false);
		}
		
		//add status container
		add(statusContainer);
		
		
		/* BASIC INFO */
		
		// privacy settings for basic info
		boolean isBasicInfoAllowed = profile.isUserXBasicInfoVisibleByUserY(userUuid, currentUserId, friend);

		WebMarkupContainer basicInfoContainer = new WebMarkupContainer("mainSectionContainer_basic");
		basicInfoContainer.setOutputMarkupId(true);
		
		//setup info
		String nickname = sakaiPerson.getNickname();
		Date dateOfBirth = sakaiPerson.getDateOfBirth();
		String birthday = "";
		int visibleFieldCount_basic = 0;
		
		if(dateOfBirth != null) {
			
			if(profile.isBirthYearVisible(userUuid)) {
				birthday = ProfileUtils.convertDateToString(dateOfBirth, ProfileConstants.DEFAULT_DATE_FORMAT);
			} else {
				birthday = ProfileUtils.convertDateToString(dateOfBirth, ProfileConstants.DEFAULT_DATE_FORMAT_HIDE_YEAR);
			}
		}
		
		//heading
		basicInfoContainer.add(new Label("mainSectionHeading_basic", new ResourceModel("heading.basic")));
		
		//nickname
		WebMarkupContainer nicknameContainer = new WebMarkupContainer("nicknameContainer");
		nicknameContainer.add(new Label("nicknameLabel", new ResourceModel("profile.nickname")));
		nicknameContainer.add(new Label("nickname", nickname));
		basicInfoContainer.add(nicknameContainer);
		if(StringUtils.isBlank(nickname)) {
			nickname=""; //for the 'add friend' link
			nicknameContainer.setVisible(false);
		} else {
			visibleFieldCount_basic++;
		}
		
		//birthday
		WebMarkupContainer birthdayContainer = new WebMarkupContainer("birthdayContainer");
		birthdayContainer.add(new Label("birthdayLabel", new ResourceModel("profile.birthday")));
		birthdayContainer.add(new Label("birthday", birthday));
		basicInfoContainer.add(birthdayContainer);
		if(StringUtils.isBlank(birthday)) {
			birthdayContainer.setVisible(false);
		} else {
			visibleFieldCount_basic++;
		}
		
		add(basicInfoContainer);
		
		//if nothing/not allowed, hide whole panel
		if(visibleFieldCount_basic == 0 || !isBasicInfoAllowed) {
			basicInfoContainer.setVisible(false);
		} else {
			visibleContainerCount++;
		}
		
		
		
		/* CONTACT INFO */
		
		// privacy settings for contact info
		boolean isContactInfoAllowed = profile.isUserXContactInfoVisibleByUserY(userUuid, currentUserId, friend);

		WebMarkupContainer contactInfoContainer = new WebMarkupContainer("mainSectionContainer_contact");
		contactInfoContainer.setOutputMarkupId(true);
		
		//get info
		String email = sakaiProxy.getUserEmail(userUuid); //must come from SakaiProxy
		String homepage = sakaiPerson.getLabeledURI();
		String workphone = sakaiPerson.getTelephoneNumber();
		String homephone = sakaiPerson.getHomePhone();
		String mobilephone = sakaiPerson.getMobile();
		String facsimile = sakaiPerson.getFacsimileTelephoneNumber();
		
		int visibleFieldCount_contact = 0;
		
		//heading
		contactInfoContainer.add(new Label("mainSectionHeading_contact", new ResourceModel("heading.contact")));
		
		//email
		WebMarkupContainer emailContainer = new WebMarkupContainer("emailContainer");
		emailContainer.add(new Label("emailLabel", new ResourceModel("profile.email")));
		emailContainer.add(new Label("email", email));
		contactInfoContainer.add(emailContainer);
		if(StringUtils.isBlank(email)) {
			emailContainer.setVisible(false);
		} else {
			visibleFieldCount_contact++;
		}
		
		//homepage
		WebMarkupContainer homepageContainer = new WebMarkupContainer("homepageContainer");
		homepageContainer.add(new Label("homepageLabel", new ResourceModel("profile.homepage")));
		homepageContainer.add(new Label("homepage", homepage));
		contactInfoContainer.add(homepageContainer);
		if(StringUtils.isBlank(homepage)) {
			homepageContainer.setVisible(false);
		} else {
			visibleFieldCount_contact++;
		}
		
		//work phone
		WebMarkupContainer workphoneContainer = new WebMarkupContainer("workphoneContainer");
		workphoneContainer.add(new Label("workphoneLabel", new ResourceModel("profile.phone.work")));
		workphoneContainer.add(new Label("workphone", workphone));
		contactInfoContainer.add(workphoneContainer);
		if(StringUtils.isBlank(workphone)) {
			workphoneContainer.setVisible(false);
		} else {
			visibleFieldCount_contact++;
		}
		
		//home phone
		WebMarkupContainer homephoneContainer = new WebMarkupContainer("homephoneContainer");
		homephoneContainer.add(new Label("homephoneLabel", new ResourceModel("profile.phone.home")));
		homephoneContainer.add(new Label("homephone", homephone));
		contactInfoContainer.add(homephoneContainer);
		if(StringUtils.isBlank(homephone)) {
			homephoneContainer.setVisible(false);
		} else {
			visibleFieldCount_contact++;
		}
		
		//mobile phone
		WebMarkupContainer mobilephoneContainer = new WebMarkupContainer("mobilephoneContainer");
		mobilephoneContainer.add(new Label("mobilephoneLabel", new ResourceModel("profile.phone.mobile")));
		mobilephoneContainer.add(new Label("mobilephone", mobilephone));
		contactInfoContainer.add(mobilephoneContainer);
		if(StringUtils.isBlank(mobilephone)) {
			mobilephoneContainer.setVisible(false);
		} else {
			visibleFieldCount_contact++;
		}
		
		//facsimile
		WebMarkupContainer facsimileContainer = new WebMarkupContainer("facsimileContainer");
		facsimileContainer.add(new Label("facsimileLabel", new ResourceModel("profile.phone.facsimile")));
		facsimileContainer.add(new Label("facsimile", facsimile));
		contactInfoContainer.add(facsimileContainer);
		if(StringUtils.isBlank(facsimile)) {
			facsimileContainer.setVisible(false);
		} else {
			visibleFieldCount_contact++;
		}
		
		add(contactInfoContainer);
		
		//if nothing/not allowed, hide whole panel
		if(visibleFieldCount_contact == 0 || !isContactInfoAllowed) {
			contactInfoContainer.setVisible(false);
		} else {
			visibleContainerCount++;
		}
		
		
		/* ACADEMIC INFO */
		
		// privacy settings for contact info
		//boolean isAcademicInfoAllowed = profile.isUserXContactInfoVisibleByUserY(userUuid, currentUserId, friend);
		boolean isAcademicInfoAllowed = true;
		
		WebMarkupContainer academicInfoContainer = new WebMarkupContainer("mainSectionContainer_academic");
		academicInfoContainer.setOutputMarkupId(true);
		
		//get info
		String department = sakaiPerson.getOrganizationalUnit();
		String position = sakaiPerson.getTitle();
		String school = sakaiPerson.getCampus();
		String room = sakaiPerson.getRoomNumber();
		String course = sakaiPerson.getEducationCourse();
		String subjects = sakaiPerson.getEducationSubjects();
		
		int visibleFieldCount_academic = 0;
		
		//heading
		academicInfoContainer.add(new Label("mainSectionHeading_academic", new ResourceModel("heading.academic")));
		
		//department
		WebMarkupContainer departmentContainer = new WebMarkupContainer("departmentContainer");
		departmentContainer.add(new Label("departmentLabel", new ResourceModel("profile.department")));
		departmentContainer.add(new Label("department", department));
		academicInfoContainer.add(departmentContainer);
		if(StringUtils.isBlank(department)) {
			departmentContainer.setVisible(false);
		} else {
			visibleFieldCount_academic++;
		}
		
		//position
		WebMarkupContainer positionContainer = new WebMarkupContainer("positionContainer");
		positionContainer.add(new Label("positionLabel", new ResourceModel("profile.position")));
		positionContainer.add(new Label("position", position));
		academicInfoContainer.add(positionContainer);
		if(StringUtils.isBlank(position)) {
			positionContainer.setVisible(false);
		} else {
			visibleFieldCount_academic++;
		}
		
		//school
		WebMarkupContainer schoolContainer = new WebMarkupContainer("schoolContainer");
		schoolContainer.add(new Label("schoolLabel", new ResourceModel("profile.school")));
		schoolContainer.add(new Label("school", school));
		academicInfoContainer.add(schoolContainer);
		if(StringUtils.isBlank(school)) {
			schoolContainer.setVisible(false);
		} else {
			visibleFieldCount_academic++;
		}
		
		//room
		WebMarkupContainer roomContainer = new WebMarkupContainer("roomContainer");
		roomContainer.add(new Label("roomLabel", new ResourceModel("profile.room")));
		roomContainer.add(new Label("room", room));
		academicInfoContainer.add(roomContainer);
		if(StringUtils.isBlank(room)) {
			roomContainer.setVisible(false);
		} else {
			visibleFieldCount_academic++;
		}
		
		//course
		WebMarkupContainer courseContainer = new WebMarkupContainer("courseContainer");
		courseContainer.add(new Label("courseLabel", new ResourceModel("profile.course")));
		courseContainer.add(new Label("course", course));
		academicInfoContainer.add(courseContainer);
		if(StringUtils.isBlank(course)) {
			courseContainer.setVisible(false);
		} else {
			visibleFieldCount_academic++;
		}
		
		//subjects
		WebMarkupContainer subjectsContainer = new WebMarkupContainer("subjectsContainer");
		subjectsContainer.add(new Label("subjectsLabel", new ResourceModel("profile.subjects")));
		subjectsContainer.add(new Label("subjects", subjects));
		academicInfoContainer.add(subjectsContainer);
		if(StringUtils.isBlank(subjects)) {
			subjectsContainer.setVisible(false);
		} else {
			visibleFieldCount_academic++;
		}
		
		add(academicInfoContainer);
		
		//if nothing/not allowed, hide whole panel
		if(visibleFieldCount_academic == 0 || !isAcademicInfoAllowed) {
			academicInfoContainer.setVisible(false);
		} else {
			visibleContainerCount++;
		}
		
		
		
		
		/* PERSONAL INFO */
		
		// privacy settings for basic info
		boolean isPersonalInfoAllowed = profile.isUserXPersonalInfoVisibleByUserY(userUuid, currentUserId, friend);

		WebMarkupContainer personalInfoContainer = new WebMarkupContainer("mainSectionContainer_personal");
		personalInfoContainer.setOutputMarkupId(true);
		
		//setup info
		String favouriteBooks = sakaiPerson.getFavouriteBooks();
		String favouriteTvShows = sakaiPerson.getFavouriteTvShows();
		String favouriteMovies = sakaiPerson.getFavouriteMovies();
		String favouriteQuotes = sakaiPerson.getFavouriteQuotes();
		String otherInformation = sakaiPerson.getNotes();
		int visibleFieldCount_personal = 0;
		
		//heading
		personalInfoContainer.add(new Label("mainSectionHeading_personal", new ResourceModel("heading.interests")));
		
		//favourite books
		WebMarkupContainer booksContainer = new WebMarkupContainer("booksContainer");
		booksContainer.add(new Label("booksLabel", new ResourceModel("profile.favourite.books")));
		booksContainer.add(new Label("favouriteBooks", favouriteBooks));
		personalInfoContainer.add(booksContainer);
		if(StringUtils.isBlank(favouriteBooks)) {
			booksContainer.setVisible(false);
		} else {
			visibleFieldCount_personal++;
		}
		
		//favourite tv shows
		WebMarkupContainer tvContainer = new WebMarkupContainer("tvContainer");
		tvContainer.add(new Label("tvLabel", new ResourceModel("profile.favourite.tv")));
		tvContainer.add(new Label("favouriteTvShows", favouriteTvShows));
		personalInfoContainer.add(tvContainer);
		if(StringUtils.isBlank(favouriteTvShows)) {
			tvContainer.setVisible(false);
		} else {
			visibleFieldCount_personal++;
		}
		
		//favourite movies
		WebMarkupContainer moviesContainer = new WebMarkupContainer("moviesContainer");
		moviesContainer.add(new Label("moviesLabel", new ResourceModel("profile.favourite.movies")));
		moviesContainer.add(new Label("favouriteMovies", favouriteMovies));
		personalInfoContainer.add(moviesContainer);
		if(StringUtils.isBlank(favouriteMovies)) {
			moviesContainer.setVisible(false);
		} else {
			visibleFieldCount_personal++;
		}
		
		//favourite quotes
		WebMarkupContainer quotesContainer = new WebMarkupContainer("quotesContainer");
		quotesContainer.add(new Label("quotesLabel", new ResourceModel("profile.favourite.quotes")));
		quotesContainer.add(new Label("favouriteQuotes", favouriteQuotes));
		personalInfoContainer.add(quotesContainer);
		if(StringUtils.isBlank(favouriteQuotes)) {
			quotesContainer.setVisible(false);
		} else {
			visibleFieldCount_personal++;
		}
		
		//favourite quotes
		WebMarkupContainer otherContainer = new WebMarkupContainer("otherContainer");
		otherContainer.add(new Label("otherLabel", new ResourceModel("profile.other")));
		otherContainer.add(new Label("otherInformation", otherInformation));
		personalInfoContainer.add(otherContainer);
		if(StringUtils.isBlank(otherInformation)) {
			otherContainer.setVisible(false);
		} else {
			visibleFieldCount_personal++;
		}
		
		
		add(personalInfoContainer);
		
		//if nothing/not allowed, hide whole panel
		if(visibleFieldCount_personal == 0 || !isPersonalInfoAllowed) {
			personalInfoContainer.setVisible(false);
		} else {
			visibleContainerCount++;
		}
		
		
		
		/* NO INFO VISIBLE MESSAGE (hide if some visible) */
		Label noContainersVisible = new Label ("noContainersVisible", new ResourceModel("text.view.profile.nothing"));
		noContainersVisible.setOutputMarkupId(true);
		add(noContainersVisible);
		
		if(visibleContainerCount > 0) {
			noContainersVisible.setVisible(false);
		}
		
		
		
		/* SIDELINKS */
		WebMarkupContainer sideLinks = new WebMarkupContainer("sideLinks");
		
		//ADD FRIEND MODAL WINDOW
		final ModalWindow addFriendWindow = new ModalWindow("addFriendWindow");

		//FRIEND LINK/STATUS
		final AjaxLink addFriendLink = new AjaxLink("addFriendLink") {
			private static final long serialVersionUID = 1L;

			public void onClick(AjaxRequestTarget target) {
    			addFriendWindow.show(target);
			}
		};
		
		final Label addFriendLabel = new Label("addFriendLabel");
		addFriendLink.add(addFriendLabel);
		
		//setup link/label and windows
		if(friend) {
			addFriendLabel.setModel(new ResourceModel("text.friend.confirmed"));
    		addFriendLink.add(new AttributeModifier("class", true, new Model("instruction")));
			addFriendLink.setEnabled(false);
		} else if (friendRequestToThisPerson) {
			addFriendLabel.setModel(new ResourceModel("text.friend.requested"));
    		addFriendLink.add(new AttributeModifier("class", true, new Model("instruction")));
			addFriendLink.setEnabled(false);
		} else if (friendRequestFromThisPerson) {
			//TODO (confirm pending friend request link)
			//could be done by setting the content off the addFriendWindow.
			//will need to rename some links to make more generic and set the onClick and setContent in here for link and window
			addFriendLabel.setModel(new ResourceModel("text.friend.pending"));
    		addFriendLink.add(new AttributeModifier("class", true, new Model("instruction")));
			addFriendLink.setEnabled(false);
		}  else {
			addFriendLabel.setModel(new StringResourceModel("link.friend.add.name", null, new Object[]{ nickname } ));
			addFriendWindow.setContent(new AddFriend(addFriendWindow.getContentId(), addFriendWindow, friendActionModel, currentUserId, userUuid)); 
		}
		sideLinks.add(addFriendLink);
		
		
		//ADD FRIEND MODAL WINDOW HANDLER 
		addFriendWindow.setWindowClosedCallback(new ModalWindow.WindowClosedCallback() {
			private static final long serialVersionUID = 1L;

			public void onClose(AjaxRequestTarget target){
            	if(friendActionModel.isRequested()) { 
            		//friend was successfully requested, update label and link
            		addFriendLabel.setModel(new ResourceModel("text.friend.requested"));
            		addFriendLink.add(new AttributeModifier("class", true, new Model("instruction")));
            		addFriendLink.setEnabled(false);
            		target.addComponent(addFriendLink);
            	}
            }
        });
		
		add(addFriendWindow);
		
		add(sideLinks);
		
		
		
		
		/* FRIEND FEED PANEL */
		//friends feed panel
		boolean isFriendsListVisible = profile.isUserXFriendsListVisibleByUserY(userUuid, currentUserId, friend);
		
		Panel friendsFeed;
		if(isFriendsListVisible) {
			friendsFeed = new FriendsFeed("friendsFeed", userUuid, currentUserId);
		} else {
			friendsFeed = new EmptyPanel("friendsFeed");
			friendsFeed.setVisible(false);
		}
		friendsFeed.setOutputMarkupId(true);
		
		add(friendsFeed);
	
	}
	
	/**
	 * This constructor is called if we have a pageParameters object containing the userUuid as an id parameter
	 * Just redirects to normal ViewProfile(String userUuid)
	 * @param parameters
	 */
	public ViewProfile(PageParameters parameters) {
		this(parameters.getString("id"));
	}
	
	/* reinit for deserialisation (ie back button) */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		log.debug("ViewProfile has been deserialized.");
		//re-init our transient objects
		profile = getProfile();
		sakaiProxy = getSakaiProxy();
	}
	
}
