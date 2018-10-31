package fr.ynov.dap.dap.service;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.GenericUrl;

@Controller
//TODO bof by Djer Devrait ête dans le package "controller" ?
public class GoogleAccount extends GoogleService {

	public GoogleAccount() throws GeneralSecurityException, IOException {
		super();
		// TODO Auto-generated constructor stub
	}

	//TODO bof By Djer LogManager.getLogger() de Log4J ne nécéssite pas de paramètre.
	//Tu peux le laisser pour "clarifier" par rapports aux autres APIs qui nécéssite de préciser la catégorie
	private final Logger LOG = LogManager.getLogger(GoogleAccount.class);

	/**
	 * Handle the Google response.
	 * 
	 * @param request The HTTP Request
	 * @param code    The (encoded) code use by Google (token, expirationDate,...)
	 * @param session the HTTP Session
	 * @return the view to display
	 * @throws ServletException When Google account could not be connected to DaP.
	 * @throws GeneralSecurityException 
	 */
	@RequestMapping("/oAuth2Callback")
	public String oAuthCallback(@RequestParam final String code, final HttpServletRequest request,
			final HttpSession session) throws ServletException, GeneralSecurityException {
		final String decodedCode = extracCode(request);
		LOG.debug(decodedCode);
		final String redirectUri = buildRedirectUri(request, cfg.getAuth2CallbackUrl());

		final String userId = getUserid(session);
		try {
			final GoogleAuthorizationCodeFlow flow = super.getFlow();
			final TokenResponse response = flow.newTokenRequest(decodedCode).setRedirectUri(redirectUri).execute();

			final Credential credential = flow.createAndStoreCredential(response, userId);
			if (null == credential || null == credential.getAccessToken()) {
				LOG.warn("Trying to store a NULL AccessToken for user : " + userId);
			}

			if (LOG.isDebugEnabled()) {
				if (null != credential && null != credential.getAccessToken()) {
					LOG.debug("New user credential stored with userId : " + userId + "partial AccessToken : "
							+ credential.getAccessToken().substring(0, 0));
				}
			}
			// onSuccess(request, resp, credential);
		} catch (IOException e) {
			LOG.error("Exception while trying to store user Credential", e);
			throw new ServletException("Error while trying to conenct Google Account");
		}

		//TODO bof by Djer es-tu sur d'avoir un mapping sur / ?
		return "redirect:";
	}

	/**
	 * retrieve the User ID in Session.
	 * 
	 * @param session the HTTP Session
	 * @return the current User Id in Session
	 * @throws ServletException if no User Id in session
	 */
	private String getUserid(final HttpSession session) throws ServletException {
		String userId = null;
		if (null != session && null != session.getAttribute("userId")) {
			userId = (String) session.getAttribute("userId");
		}

		if (null == userId) {
			LOG.error("userId in Session is NULL in Callback");
			throw new ServletException("Error when trying to add Google acocunt : userId is NULL is User Session");
		}
		return userId;
	}

	/**
	 * Extract OAuth2 Google code (from URL) and decode it.
	 * 
	 * @param request the HTTP request to extract OAuth2 code
	 * @return the decoded code
	 * @throws ServletException if the code cannot be decoded
	 */
	private String extracCode(final HttpServletRequest request) throws ServletException {
		final StringBuffer buf = request.getRequestURL();
		if (null != request.getQueryString()) {
			buf.append('?').append(request.getQueryString());
		}
		final AuthorizationCodeResponseUrl responseUrl = new AuthorizationCodeResponseUrl(buf.toString());
		final String decodeCode = responseUrl.getCode();

		if (decodeCode == null) {
			throw new MissingServletRequestParameterException("code", "String");
		}

		if (null != responseUrl.getError()) {
			LOG.error("Error when trying to add Google acocunt : " + responseUrl.getError());
			throw new ServletException("Error when trying to add Google acocunt");
			// onError(request, resp, responseUrl);
		}

		return decodeCode;
	}

	@RequestMapping(value="/account/add/{userId}", method = RequestMethod.GET)
	public String addAccount(@PathVariable final String userId, final HttpServletRequest request,
			final HttpSession session) throws GeneralSecurityException {
		String response = "errorOccurs";
        GoogleAuthorizationCodeFlow flow;
        Credential credential = null;
        try {
            flow = super.getFlow();
            credential = flow.loadCredential(userId);

            if (credential != null && credential.getAccessToken() != null) {
                response = "AccountAlreadyAdded";
            } else {
                // redirect to the authorization flow
                final AuthorizationCodeRequestUrl authorizationUrl = flow.newAuthorizationUrl();
                authorizationUrl.setRedirectUri(buildRedirectUri(request, cfg.getAuth2CallbackUrl()));
                // store userId in session for CallBack Access
                session.setAttribute("userId", userId);
                return "redirect:"+authorizationUrl.build();
                
            }
        } catch (IOException e) {
            LOG.error("Error while loading credential (or Google Flow)", e);
        }
        return response;
	}

	/**
	 * Build a current host (and port) absolute URL.
	 * 
	 * @param req         The current HTTP request to extract schema, host, port
	 *                    informations
	 * @param destination the "path" to the resource
	 * @return an absolute URI
	 */
	protected String buildRedirectUri(final HttpServletRequest req, final String destination) {
		final GenericUrl url = new GenericUrl(req.getRequestURL().toString());
		url.setRawPath(destination);
		return url.build();
	}

}
