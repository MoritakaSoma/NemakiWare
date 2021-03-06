/*******************************************************************************
 * Copyright (c) 2013 aegif.
 *
 * This file is part of NemakiWare.
 *
 * NemakiWare is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NemakiWare is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public Licensealong with NemakiWare. If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     linzhixing(https://github.com/linzhixing) - initial API and implementation
 ******************************************************************************/
package jp.aegif.nemaki.cmis.factory.auth.impl;

import jp.aegif.nemaki.businesslogic.PrincipalService;
import jp.aegif.nemaki.cmis.factory.auth.AuthenticationService;
import jp.aegif.nemaki.cmis.factory.auth.Token;
import jp.aegif.nemaki.cmis.factory.auth.TokenService;
import jp.aegif.nemaki.cmis.factory.info.RepositoryInfoMap;
import jp.aegif.nemaki.model.User;
import jp.aegif.nemaki.util.PropertyManager;
import jp.aegif.nemaki.util.constant.CallContextKey;
import jp.aegif.nemaki.util.constant.PropertyKey;

import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.server.impl.CallContextImpl;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Authentication Service implementation.
 */
public class AuthenticationServiceImpl implements AuthenticationService {

	private static final Log log = LogFactory.getLog(AuthenticationServiceImpl.class);

	private PrincipalService principalService;
	private TokenService tokenService;
	private PropertyManager propertyManager;
	private RepositoryInfoMap repositoryInfoMap;

	public boolean login(CallContext callContext) {
		// Set flag of SuperUsers
		String suId = repositoryInfoMap.getSuperUsers().getId();
		((CallContextImpl)callContext).put(CallContextKey.IS_SU, 
				suId.equals(callContext.getRepositoryId()));
		
		// SSO
		if (loginWithExternalAuth(callContext)) {
			return true;
		}

		// Token for Basic auth
		if (loginWithToken(callContext)) {
			return true;
		}

		// Basic auth
		return loginWithBasicAuth(callContext);
	}

	private boolean loginWithExternalAuth(CallContext callContext) {
		final String repositoryId = "bedroom"; // TODO hard coding

		String proxyHeaderKey = propertyManager.readValue(PropertyKey.EXTERNAL_AUTHENTICATION_PROXY_HEADER);
		if(StringUtils.isBlank(proxyHeaderKey)){
			return false;
		}
		String proxyUserId = (String) callContext.get(proxyHeaderKey);
		if (StringUtils.isBlank(proxyUserId)) {
			log.warn("Not authenticated user");
			return false;
		} else {
			User user = principalService.getUserById(repositoryId, proxyUserId);
			if (user == null) {
				User newUser = new User(proxyUserId, proxyUserId, "", "", "",
						BCrypt.hashpw(proxyUserId, BCrypt.gensalt()));
				principalService.createUser(repositoryId, newUser);
				log.debug("Authenticated userId=" + newUser.getUserId());
			} else {
				log.debug("Authenticated userId=" + user.getUserId());
				
				//Admin check
				boolean isAdmin = user.isAdmin() == null ? false : true;
				setAdminFlagInContext(callContext, isAdmin);
			}
			return true;
		}
	}

	private boolean loginWithToken(CallContext callContext) {
		String userName = callContext.getUsername();
		String token;
		if (callContext.get("nemaki_auth_token") == null) {
			return false;
		} else {
			token = (String) callContext.get("nemaki_auth_token");
			if (StringUtils.isBlank(token)) {
				return false;
			}
		}
		Object _app = callContext.get("nemaki_auth_token_app");
		String app = (_app == null) ? "" : (String) _app;

		if (authenticateUserByToken(app, callContext.getRepositoryId(), userName, token)) {
			if (authenticateAdminByToken(callContext.getRepositoryId(), userName)) {
				setAdminFlagInContext(callContext, true);
			} else {
				setAdminFlagInContext(callContext, false);
			}
			return true;
		}

		return false;
	}

	private boolean loginWithBasicAuth(CallContext callContext) {
		//Check repositoryId exists
		if(!repositoryInfoMap.contains(callContext.getRepositoryId())){
			return false;
		}
		
		// Basic auth with id/password
		User user = getAuthenticatedUser(callContext.getRepositoryId(), callContext.getUsername(), callContext.getPassword());
		if (user == null)
			return false;
		boolean isAdmin = user.isAdmin() == null ? false : true;
		setAdminFlagInContext(callContext, isAdmin);
		return true;
	}
	
	private void setAdminFlagInContext(CallContext callContext, Boolean isAdmin) {
		((CallContextImpl) callContext).put(CallContextKey.IS_ADMIN, isAdmin);
	}

	private boolean authenticateUserByToken(String app, String repositoryId, String userName, String token) {
		Token registeredToken = tokenService.getToken(app, repositoryId, userName);
		if (registeredToken == null) {
			return false;
		} else {
			long expiration = registeredToken.getExpiration();
			if (System.currentTimeMillis() > expiration) {
				return false;
			} else {
				String _registeredToken = registeredToken.getToken();
				return StringUtils.isNotEmpty(_registeredToken) && _registeredToken.equals(token);
			}
		}
	}

	private boolean authenticateAdminByToken(String repositoryId, String userName) {
		return tokenService.isAdmin(repositoryId, userName);
	}

	private User getAuthenticatedUser(String repositoryId, String userName, String password) {
		User u = principalService.getUserById(repositoryId, userName);

		// succeeded
		if (u != null) {
			if (passwordMatches(password, u.getPasswordHash())) {
				log.debug("[" + userName + "]Authentication succeeded");
				return u;
			}
		}

		// Check anonymous
		String anonymousId = principalService.getAnonymous(repositoryId);
		if (StringUtils.isNotBlank(anonymousId) && anonymousId.equals(userName)) {
			if (u != null) {
				log.warn(anonymousId + " should have not been registered in the database.");
			}
			return null;
		}

		return null;
	}
	
	/**
	 * Check whether a password matches a hash.
	 */
	private boolean passwordMatches(String candidate, String hashed) {
		return BCrypt.checkpw(candidate, hashed);
	}

	public void setPrincipalService(PrincipalService principalService) {
		this.principalService = principalService;
	}

	public void setTokenService(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	public void setPropertyManager(PropertyManager propertyManager) {
		this.propertyManager = propertyManager;
	}

	public void setRepositoryInfoMap(RepositoryInfoMap repositoryInfoMap) {
		this.repositoryInfoMap = repositoryInfoMap;
	}
}