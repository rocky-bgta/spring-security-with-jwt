# spring-security

Spring Boot JWT authentication example using Spring Security, a custom
`UserDetailsService`, BCrypt password hashing, JWT request authorization,
Google Login with OAuth 2.0/OpenID Connect, and PostgreSQL-backed users and
roles.

This README explains how this specific project works. It intentionally uses the
actual class names, method names, endpoints, and security configuration from this
repository.

## 1. Project Overview

This project exposes a small role-based API secured with Spring Security and
JWT.

The project uses:

- Spring Boot `2.5.0`
- Java `11` target
- Spring Security
- Spring Security OAuth2 Client for Google Login
- Spring Data JPA
- PostgreSQL
- `io.jsonwebtoken:jjwt:0.9.1`
- BCrypt password hashing
- JWT authentication for secured APIs

The API still uses application JWTs. Google is only another login method that
proves identity first, then this application creates its normal JWT.

## JDK Requirement

This project is configured with:

```xml
<java.version>11</java.version>
```

Use JDK 11 or newer to compile and run the project. JDK 8 is not enough because
it cannot compile a module configured for JVM target 11.

This project has been tested successfully with JDK 21 after pinning Lombok to a
modern version in `pom.xml`.

For IntelliJ IDEA:

- Set the Project SDK to JDK 11 or newer.
- Set the Maven runner JRE to JDK 11 or newer.
- Do not let the IDE use JDK 8 as the fallback SDK for this module.

Authentication answers the question: "Who is this user?"

In this project authentication happens in two places:

- Login: `AuthenticationController.createAuthenticationToken()` authenticates a
  username and password through Spring Security's `AuthenticationManager`.
- Later requests: `CustomJwtAuthenticationFilter.doFilterInternal()` validates
  the JWT and stores an authenticated object in `SecurityContextHolder`.

Authorization answers the question: "Is this authenticated user allowed to call
this endpoint?"

Authorization rules are configured in the `SecurityConfiguration.securityFilterChain(...)`
bean:

```java
.antMatchers("/authenticate", "/register", "/oauth2/**", "/login/oauth2/**").permitAll()
.antMatchers("/api/secure/**").authenticated()
.antMatchers("/admin-user").hasRole("ADMIN")
.antMatchers("/normal-user").hasAnyRole("ADMIN","USER")
.anyRequest().authenticated()
```

JWT is used after successful login. The client receives a token from
`/authenticate` and sends it back in later requests:

```http
Authorization: Bearer <jwt-token>
```

Public endpoints:

- `POST /authenticate`
- `POST /register`

Protected endpoints:

- `GET /api/secure/info` requires any authenticated user
- `GET /admin-user` requires `ROLE_ADMIN`
- `GET /normal-user` requires `ROLE_ADMIN` or `ROLE_USER`
- `GET /refreshtoken` requires the special expired-token refresh flow implemented
  in `CustomJwtAuthenticationFilter`
- Any other endpoint requires authentication because of `.anyRequest().authenticated()`

Important note: this project does not define a modern `SecurityFilterChain`
`@Bean`. It uses the older Spring Security style:

```java
public class SecurityConfiguration
```

Spring creates and executes a security filter chain from the
`SecurityFilterChain` bean. This project still uses `antMatchers(...)` because
it is on Spring Boot `2.5.0`.

## 2. Important Classes

### `SpringSecurityApplication`

Purpose:
Starts the Spring Boot application.

Why it is needed:
It enables component scanning and auto-configuration through `@SpringBootApplication`.

Called by:
The JVM when the application starts.

Calls:
`SpringApplication.run(SpringSecurityApplication.class, args)`.

### `SecurityConfiguration`

Purpose:
Defines the Spring Security setup for authentication, authorization, session
management, exception handling, and JWT filter registration.

Why it is needed:
Without this class, the app would not know which endpoints are public, which
roles are required, which password encoder to use, or where the JWT filter
belongs in the filter chain.

Called by:
Spring Security during application startup.

Calls:

- `CustomUserDetailsService` through `AuthenticationManagerBuilder`
- `JwtAuthenticationEntryPoint` for authentication failures
- `CustomJwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- `BCryptPasswordEncoder` through the `PasswordEncoder` bean

### `CustomJwtAuthenticationFilter`

Purpose:
Intercepts incoming HTTP requests and checks whether a JWT exists in the
`Authorization` header.

Why it is needed:
JWT authentication is stateless. Spring Security does not automatically know who
the user is on each request. This filter validates the token and rebuilds the
authenticated user for the current request.

Called by:
Spring Security's filter chain. It is registered with:

```java
.addFilterBefore(customJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

Calls:

- `JwtTokenUtil.getUsernameFromToken(...)`
- `CustomUserDetailsService.loadUserByUsername(...)`
- `JwtTokenUtil.validateToken(...)`
- `SecurityContextHolder.getContext().setAuthentication(...)`

### `JwtTokenUtil`

Purpose:
Generates JWTs, extracts claims from JWTs, extracts the username, checks
expiration, and validates tokens.

Why it is needed:
All low-level JWT operations are centralized here instead of being placed inside
controllers or filters.

Called by:

- `AuthenticationController`
- `CustomJwtAuthenticationFilter`

Calls:
The JJWT library:

```java
Jwts.builder()
Jwts.parser()
```

### `JwtAuthenticationEntryPoint`

Purpose:
Returns a JSON `401 Unauthorized` response when authentication fails.

Why it is needed:
In a REST API, failed authentication should return a proper HTTP response
instead of redirecting to a login page.

Called by:
Spring Security exception handling configured here:

```java
.exceptionHandling()
.authenticationEntryPoint(jwtAuthenticationEntryPoint)
```

Calls:
`HttpServletResponse` to set status, content type, and JSON error body.

### `CustomUserDetailsService`

Purpose:
Loads a user by username and converts database roles into Spring Security
authorities.

Why it is needed:
Spring Security's `DaoAuthenticationProvider` does not know this project's
database schema. It depends on `UserDetailsService` to load user data.

Called by:

- The implicit `DaoAuthenticationProvider` during username/password login
- `CustomJwtAuthenticationFilter` during JWT request authentication
- `AuthenticationController` after successful login to generate a token

Calls:

- `UserRepository.findByUsername(username)`
- `new User(username, password, authorities)` from Spring Security

### `AuthenticationController`

Purpose:
Handles login and refresh-token endpoints.

Why it is needed:
It exposes API endpoints that clients can call to obtain or refresh JWTs.

Called by:
Spring MVC when requests match:

- `POST /authenticate`
- `GET /refreshtoken`

Calls:

- `AuthenticationManager.authenticate(...)`
- `CustomUserDetailsService.loadUserByUsername(...)`
- `JwtTokenUtil.generateToken(...)`
- `JwtTokenUtil.doGenerateRefreshToken(...)`

### `RegisterController`

Purpose:
Handles user registration.

Why it is needed:
It allows a user payload to be stored in the database with an encoded password.

Called by:
Spring MVC for:

- `POST /register`

Calls:
`UserService.saveUser(...)`.

### `UserServiceImpl`

Purpose:
Encodes the user's password and saves the user through `UserRepository`.

Why it is needed:
Passwords must be stored as hashes, not plaintext. This class applies
`PasswordEncoder.encode(...)` before saving.

Called by:
`RegisterController.saveUser(...)`.

Calls:

- `PasswordEncoder.encode(...)`
- `UserRepository.save(...)`

### `UserRepository`

Purpose:
Provides database access for `UserEntity`.

Why it is needed:
Authentication needs to look up users by username, and registration needs to
save users.

Called by:

- `CustomUserDetailsService`
- `UserServiceImpl`

Calls:
Spring Data JPA generated repository methods.

Important method:

```java
UserEntity findByUsername(String username);
```

### `UserEntity`

Purpose:
Represents the persisted user table row.

Why it is needed:
It stores username, encoded password, status fields, and user roles.

Called by:
JPA, `UserRepository`, `CustomUserDetailsService`, and `UserServiceImpl`.

Calls:
No security component directly. It is a data entity.

### `RoleEntity`

Purpose:
Represents a persisted role row.

Why it is needed:
`CustomUserDetailsService` converts `RoleEntity.roleName` into Spring Security
authorities.

Called by:
JPA and `CustomUserDetailsService`.

Calls:
No security component directly. It is a data entity.

### `AuthenticationRequest`

Purpose:
Represents the login request body.

Why it is needed:
Spring MVC binds incoming JSON username and password fields into this object.

Called by:
`AuthenticationController.createAuthenticationToken(...)`.

Calls:
No security component directly.

### `AuthenticationResponse`

Purpose:
Represents the login or refresh response body.

Why it is needed:
It wraps the generated JWT in a JSON response.

Called by:
`AuthenticationController`.

Calls:
No security component directly.

### `UserModel`

Purpose:
Represents the registration request and response model.

Why it is needed:
`RegisterController` receives this model and `UserServiceImpl` maps it into
`UserEntity`.

Called by:

- `RegisterController`
- `UserServiceImpl`

Calls:
No security component directly.

## 3. Login Flow - Step by Step

Actual login endpoint:

```http
POST /authenticate
```

Request body:

```json
{
  "username": "tuli",
  "password": "tuli"
}
```

Flow:

```text
Client
  |
  | POST /authenticate
  v
AuthenticationController.createAuthenticationToken()
  |
  | new UsernamePasswordAuthenticationToken(username, password)
  v
AuthenticationManager.authenticate(...)
  |
  v
Implicit DaoAuthenticationProvider
  |
  v
CustomUserDetailsService.loadUserByUsername(username)
  |
  v
UserRepository.findByUsername(username)
  |
  v
BCryptPasswordEncoder checks password
  |
  v
Authentication successful
  |
  v
AuthenticationController loads UserDetails again
  |
  v
JwtTokenUtil.generateToken(userDetails)
  |
  v
ResponseEntity.ok(new AuthenticationResponse(token))
```

Step 1:

Class:
`AuthenticationController`

Method:
`createAuthenticationToken(@RequestBody AuthenticationRequest authenticationRequest)`

What happens:
Spring MVC maps the request body into `AuthenticationRequest`.

Object passed next:
The username and password are wrapped in:

```java
new UsernamePasswordAuthenticationToken(
    authenticationRequest.getUsername(),
    authenticationRequest.getPassword()
)
```

Why required:
`AuthenticationManager` works with Spring Security `Authentication` objects, not
with this project's `AuthenticationRequest` DTO.

Step 2:

Class:
`AuthenticationController`

Method call:

```java
authenticationManager.authenticate(...)
```

Object passed next:
`UsernamePasswordAuthenticationToken` containing the raw username and password.

Why required:
The controller should not manually check passwords. It delegates authentication
to Spring Security.

What happens when authentication fails:

- `DisabledException` is caught and wrapped as `new Exception("USER_DISABLED", e)`
- `BadCredentialsException` is caught and wrapped as
  `new Exception("INVALID_CREDENTIALS", e)`

Step 3:

Class:
Spring Security's `AuthenticationManager`

Method:
`authenticate(Authentication authentication)`

Object passed next:
The same `UsernamePasswordAuthenticationToken`.

Why required:
`AuthenticationManager` is the entry point into Spring Security authentication.
It delegates to an `AuthenticationProvider` that knows how to authenticate this
token type.

Step 4:

Class:
Spring Security's `DaoAuthenticationProvider`

Method:
Internal provider authentication method.

Object passed next:
The username is passed to `CustomUserDetailsService.loadUserByUsername(...)`.

Why required:
This project does not define a custom `AuthenticationProvider` bean. Spring
creates a `DaoAuthenticationProvider` because of this configuration:

```java
auth.userDetailsService(customUserDetailsService)
    .passwordEncoder(passwordEncoder());
```

Step 5:

Class:
`CustomUserDetailsService`

Method:

```java
loadUserByUsername(String username)
```

Object passed next:
The username string.

Why required:
Spring Security needs user data in the `UserDetails` format.

What happens:

```java
userModel = userRepository.findByUsername(username);
```

If the user exists, roles are converted:

```java
roleList.add(new SimpleGrantedAuthority(roleEntity.getRoleName()));
```

Then Spring Security's built-in `User` object is returned:

```java
return new User(userModel.getUsername(), userModel.getPassword(), roleList);
```

If the user does not exist:

```java
throw new UsernameNotFoundException("User not found with the name" + username);
```

Step 6:

Class:
`BCryptPasswordEncoder`

Method:
Spring Security internally calls `matches(rawPassword, encodedPassword)`.

Why required:
The database stores BCrypt hashes. The raw password from the login request must
be checked against the encoded hash without manually decoding or comparing
plaintext.

Step 7:

Class:
`AuthenticationController`

Method:
`createAuthenticationToken(...)`

What happens after successful authentication:
The controller loads the same user again:

```java
final UserDetails userDetails =
    userDetailsService.loadUserByUsername(authenticationRequest.getUsername());
```

Then it generates a JWT:

```java
final String token = jwtTokenUtil.generateToken(userDetails);
```

And returns:

```java
return ResponseEntity.ok(new AuthenticationResponse(token));
```

## 4. Authenticated Request Flow

For protected requests, the client sends:

```http
Authorization: Bearer <JWT>
```

Flow:

```text
Client
  |
  | Authorization: Bearer <JWT>
  v
Spring Security filter chain
  |
  v
CustomJwtAuthenticationFilter.doFilterInternal(...)
  |
  | request.getHeader("Authorization")
  v
Extract JWT
  |
  | jwtTokenUtil.getUsernameFromToken(jwtToken)
  v
Extract username
  |
  | customUserDetailsService.loadUserByUsername(username)
  v
Load UserDetails and authorities
  |
  | jwtTokenUtil.validateToken(jwtToken, userDetails)
  v
Validate signature, subject, and expiration
  |
  v
Create UsernamePasswordAuthenticationToken
  |
  v
SecurityContextHolder.getContext().setAuthentication(...)
  |
  v
Authorization check from SecurityConfiguration
  |
  v
Controller method
```

Step 1:

Class:
`CustomJwtAuthenticationFilter`

Method:

```java
doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
```

What happens:
The filter reads:

```java
final String requestTokenHeader = request.getHeader("Authorization");
```

Why required:
JWT is sent by the client through the `Authorization` header.

Step 2:

Class:
`CustomJwtAuthenticationFilter`

Method:
`doFilterInternal(...)`

What happens:
The filter checks:

```java
if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
    jwtToken = requestTokenHeader.substring(7);
    username = jwtTokenUtil.getUsernameFromToken(jwtToken);
}
```

Object passed next:
The extracted JWT string is passed to `JwtTokenUtil.getUsernameFromToken(...)`.

Why required:
The token subject contains the username. The username is needed to reload the
user and authorities from the database.

Step 3:

Class:
`JwtTokenUtil`

Method:

```java
getUsernameFromToken(String token)
```

What happens:
It reads the JWT subject:

```java
return getClaimFromToken(token, Claims::getSubject);
```

Step 4:

Class:
`CustomJwtAuthenticationFilter`

Method:
`doFilterInternal(...)`

What happens:

```java
if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    UserDetails userDetails = this.customUserDetailsService.loadUserByUsername(username);
    ...
}
```

Why required:
The filter only authenticates the request if it found a username and the current
security context does not already contain authentication.

Step 5:

Class:
`CustomUserDetailsService`

Method:
`loadUserByUsername(String username)`

What happens:
The database user and roles are loaded through `UserRepository`.

Object returned:
Spring Security's built-in `org.springframework.security.core.userdetails.User`.

Step 6:

Class:
`JwtTokenUtil`

Method:

```java
validateToken(String authToken, UserDetails userDetails)
```

What happens:

- Extracts username from the token
- Compares it with `userDetails.getUsername()`
- Checks that the token is not expired
- Converts invalid signature/malformed/unsupported token errors into
  `BadCredentialsException`
- Rethrows `ExpiredJwtException`

Step 7:

Class:
`CustomJwtAuthenticationFilter`

Method:
`doFilterInternal(...)`

What happens when token validation succeeds:

```java
UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
    new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
```

Why required:
Spring Security needs an `Authentication` object containing the principal and
authorities for the current request.

Step 8:

Class:
`CustomJwtAuthenticationFilter`

Method:
`doFilterInternal(...)`

What happens:

```java
SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
```

Why required:
This is the moment the current request becomes authenticated in Spring Security.

Step 9:

Class:
`SecurityConfiguration`

Method:
`securityFilterChain(HttpSecurity http, ...)`

What happens:
Spring Security applies authorization rules:

```java
.antMatchers("/authenticate", "/register", "/oauth2/**", "/login/oauth2/**").permitAll()
.antMatchers("/api/secure/**").authenticated()
.antMatchers("/admin-user").hasRole("ADMIN")
.antMatchers("/normal-user").hasAnyRole("ADMIN","USER")
.anyRequest().authenticated()
```

If the authenticated user has the required role, the request reaches the
controller.

## 5. SecurityContextHolder

This project sets authentication into `SecurityContextHolder` in
`CustomJwtAuthenticationFilter`.

Main authenticated JWT request:

```java
SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
```

Refresh-token special case:

```java
SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
```

The main JWT request stores this object:

```java
new UsernamePasswordAuthenticationToken(
    userDetails,
    null,
    userDetails.getAuthorities()
)
```

What is stored:

- Principal: `userDetails`
- Credentials: `null`
- Authorities: `userDetails.getAuthorities()`

Why credentials are `null`:
The password is not needed after the JWT has already been validated.

Why it must be stored:
Spring Security authorization checks read the current request's authentication
from `SecurityContextHolder`. Without this line, `.authenticated()`,
`.hasRole(...)`, and `.hasAnyRole(...)` would not know who the user is.

What happens after it is stored:

- Spring Security treats the current request as authenticated.
- The user's authorities become available for authorization checks.
- Protected controllers can be reached if the user has the required role.

How Spring Security knows the user is authenticated:
`UsernamePasswordAuthenticationToken` with authorities is considered
authenticated. The three-argument constructor creates an authenticated token.

How controllers and security rules use it:
This project's controllers do not directly read `SecurityContextHolder`, but
Spring Security's authorization layer reads it before allowing access to
`/admin-user`, `/normal-user`, or other authenticated endpoints.

Important JWT API detail:
The security context is rebuilt for each Bearer-token API request. The app uses:

```java
SessionCreationPolicy.IF_REQUIRED
```

OAuth login can create a temporary session for the Google redirect flow. API
requests still send the application JWT and are rebuilt by
`CustomJwtAuthenticationFilter`.

## 6. AuthenticationManager vs AuthenticationProvider

In this project, the login path is:

```text
AuthenticationManager
  |
  v
AuthenticationProvider
  |
  v
UserDetailsService
  |
  v
PasswordEncoder
```

### `AuthenticationManager`

Actual usage:

```java
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(
        authenticationRequest.getUsername(),
        authenticationRequest.getPassword()
    )
);
```

Responsibility:
It is the main Spring Security authentication entry point. The controller gives
it an unauthenticated `UsernamePasswordAuthenticationToken`.

Where it comes from:

```java
@Bean
@Override
public AuthenticationManager authenticationManagerBean() throws Exception {
    return super.authenticationManagerBean();
}
```

Why this bean exists:
`AuthenticationController` needs to inject `AuthenticationManager`.

### `AuthenticationProvider`

Implementation used:
`DaoAuthenticationProvider`.

Important project-specific detail:
There is no explicit class like `CustomAuthenticationProvider` and no explicit
`@Bean` of type `DaoAuthenticationProvider`.

Spring creates/configures it implicitly because of:

```java
auth.userDetailsService(customUserDetailsService).passwordEncoder(passwordEncoder());
```

Responsibility:
It knows how to authenticate username/password tokens by loading a user and
checking a password.

### `UserDetailsService`

Implementation used:
`CustomUserDetailsService`.

Responsibility:
Loads user data from this project's database and returns Spring Security
`UserDetails`.

### `PasswordEncoder`

Implementation used:
`BCryptPasswordEncoder`.

Responsibility:
Compares the raw login password with the stored BCrypt hash.

## 7. UserDetails and UserDetailsService

This project does not have a custom class that implements `UserDetails`.

Instead, `CustomUserDetailsService` returns Spring Security's built-in
`UserDetails` implementation:

```java
return new User(userModel.getUsername(), userModel.getPassword(), roleList);
```

Class that implements `UserDetailsService`:

```java
public class CustomUserDetailsService implements UserDetailsService
```

Method:

```java
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException
```

When Spring calls it:

- During login, the implicit `DaoAuthenticationProvider` calls it.
- During JWT request processing, `CustomJwtAuthenticationFilter` calls it.
- After successful login, `AuthenticationController` calls it again before
  generating the JWT.

What information it returns:

- Username
- Encoded password
- Authorities converted from `RoleEntity`

How roles are created:

```java
for (RoleEntity roleEntity : userModel.getRoleList()) {
    roleList.add(new SimpleGrantedAuthority(roleEntity.getRoleName()));
}
```

The database role names are expected to contain the `ROLE_` prefix, for example:

- `ROLE_ADMIN`
- `ROLE_USER`

That matters because this configuration:

```java
.hasRole("ADMIN")
```

checks for authority:

```text
ROLE_ADMIN
```

## 8. PasswordEncoder

Password encoder used:

```java
@Bean
public PasswordEncoder passwordEncoder(){
    return new BCryptPasswordEncoder();
}
```

Where passwords are encoded:

Class:
`UserServiceImpl`

Method:
`saveUser(UserModel userModel)`

Code:

```java
userModel.setPassword(bcryptEncoder.encode(userModel.getPassword()));
```

Where passwords are compared during login:
The comparison happens inside Spring Security's `DaoAuthenticationProvider`,
using the configured `BCryptPasswordEncoder`.

The project does not call `passwordEncoder.matches(...)` directly during login.
Spring Security does that internally after `CustomUserDetailsService` returns
the stored encoded password.

Why not compare plaintext passwords manually:

- BCrypt hashes include salt and cost.
- The stored value is not equal to the raw password.
- Manual plaintext comparison would either fail or encourage storing plaintext,
  which is unsafe.
- Delegating to `PasswordEncoder` keeps the authentication code aligned with
  Spring Security.

## 9. SecurityFilterChain

This project defines:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, ...)
```

The actual configuration:

```java
http.authenticationProvider(authenticationProvider)
    .csrf().disable()
    .formLogin().disable()
    .httpBasic().disable()
    .authorizeRequests()
    .antMatchers("/authenticate", "/register", "/oauth2/**", "/login/oauth2/**").permitAll()
    .antMatchers("/api/secure/**").authenticated()
    .antMatchers("/admin-user").hasRole("ADMIN")
    .antMatchers("/normal-user").hasAnyRole("ADMIN","USER")
    .anyRequest().authenticated()
    .and()
    .oauth2Login()
    .successHandler(googleOAuth2AuthenticationSuccessHandler)
    .failureHandler(googleOAuth2AuthenticationFailureHandler)
    .and()
    .exceptionHandling()
    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
    .and()
    .sessionManagement()
    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
    .and()
    .addFilterBefore(customJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

### `csrf().disable()`

Meaning:
Disables CSRF protection.

Why:
The secured API endpoints use JWTs sent in the `Authorization` header. Google
OAuth login uses a temporary session during the browser redirect flow.

### `formLogin().disable()`

Meaning:
Disables Spring Security's default form login page.

Why:
Login is handled by REST endpoint `POST /authenticate`.

### `httpBasic().disable()`

Meaning:
Disables HTTP Basic authentication.

Why:
The project uses JWT after login instead of sending username/password on every
request.

### `authorizeRequests()`

Meaning:
Starts endpoint authorization configuration.

Why:
The project needs role-based access rules for different endpoints.

### `antMatchers("/admin-user").hasRole("ADMIN")`

Meaning:
Only users with `ROLE_ADMIN` can access `/admin-user`.

Why:
`hasRole("ADMIN")` automatically checks for `ROLE_ADMIN`.

### `antMatchers("/normal-user").hasAnyRole("ADMIN","USER")`

Meaning:
Users with `ROLE_ADMIN` or `ROLE_USER` can access `/normal-user`.

Why:
Normal user content is available to both normal users and admins.

### `antMatchers("/authenticate","/register").permitAll()`

Meaning:
Login and registration are public.

Why:
A user cannot already have a JWT before logging in or registering.

### `anyRequest().authenticated()`

Meaning:
Any endpoint not already matched requires authentication.

Why:
This is the default protection rule for everything else, including
`/refreshtoken`.

### `exceptionHandling().authenticationEntryPoint(jwtAuthenticationEntryPoint)`

Meaning:
Authentication failures use `JwtAuthenticationEntryPoint`.

Why:
The app should return JSON `401 Unauthorized` responses instead of redirecting
to a login page.

### `sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)`

Meaning:
Spring Security may create an HTTP session when a feature needs it.

Why:
Google OAuth2 login needs a temporary session during the browser redirect flow.
Secured API requests still prove authentication by sending the application JWT.

### `addFilterBefore(customJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`

Meaning:
Run `CustomJwtAuthenticationFilter` before Spring Security's
`UsernamePasswordAuthenticationFilter`.

Why:
The JWT must be validated and `SecurityContextHolder` must be populated before
authorization rules are evaluated.

### About `requestMatchers()`

Modern Spring Security versions often use `requestMatchers(...)`. This project
uses Spring Boot `2.5.0` and the older `antMatchers(...)` style.

## 10. 401 vs 403

### 401 Unauthorized

Meaning:
The user is not authenticated.

Examples in this project:

- Calling `/normal-user` without an `Authorization` header.
- Calling `/admin-user` with an invalid JWT.
- Calling a protected endpoint with an expired JWT, except the special
  `/refreshtoken` path with `isRefreshToken: true`.

Relevant configuration:

```java
.anyRequest().authenticated()
.authenticationEntryPoint(jwtAuthenticationEntryPoint)
```

`JwtAuthenticationEntryPoint` sets:

```java
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
```

### 403 Forbidden

Meaning:
The user is authenticated, but does not have the required authority.

Example in this project:

- User `tuli` has `ROLE_USER`.
- The user sends a valid JWT.
- The user calls `/admin-user`.
- Authentication succeeds, but authorization fails because `/admin-user`
  requires `ROLE_ADMIN`.

Relevant configuration:

```java
.antMatchers("/admin-user").hasRole("ADMIN")
```

Spring Security normally handles this as an access denied case. This project
customizes the authentication entry point for `401`, but it does not define a
custom `AccessDeniedHandler` for `403`.

## 11. JWT Filter - Detailed Explanation

JWT filter class:

```java
CustomJwtAuthenticationFilter extends OncePerRequestFilter
```

Main method:

```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
```

### Read the `Authorization` header

CODE:

```java
final String requestTokenHeader = request.getHeader("Authorization");
```

MEANING:
Reads the HTTP `Authorization` header from the incoming request.

WHY:
The client sends JWTs as `Authorization: Bearer <token>`.

### Prepare token variables

CODE:

```java
String username = null;
String jwtToken = null;
```

MEANING:
Prepares variables that will be filled if a valid-looking bearer header exists.

WHY:
The filter needs both the raw token and the username extracted from the token.

### Check for Bearer token

CODE:

```java
if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
    jwtToken = requestTokenHeader.substring(7);
    username = jwtTokenUtil.getUsernameFromToken(jwtToken);
} else {
    logger.warn("JWT Token does not begin with Bearer String");
}
```

MEANING:
If the header starts with `Bearer `, remove that prefix and parse the JWT to get
the username.

WHY:
The JJWT parser needs only the token value, not the full header text.

Project-specific behavior:
This logs a warning even for public requests like `/authenticate` and
`/register` when no Bearer token is present.

### Check whether authentication is needed

CODE:

```java
if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
```

MEANING:
Continue only if a username was extracted and the current request does not
already have authentication.

WHY:
The filter should not overwrite an existing authenticated context.

### Load the user

CODE:

```java
UserDetails userDetails = this.customUserDetailsService.loadUserByUsername(username);
```

MEANING:
Loads the user and authorities from the database.

WHY:
The JWT contains the username, but the project still reloads roles from the
database so Spring Security can authorize the request.

### Validate the token

CODE:

```java
if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
```

MEANING:
Checks that the token subject matches the loaded user and that the token is not
expired. Invalid signature, malformed token, unsupported token, and illegal
argument errors become `BadCredentialsException`.

WHY:
The app must not trust a token just because it exists.

### Create authenticated token

CODE:

```java
UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
    new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
```

MEANING:
Creates an authenticated Spring Security `Authentication` object.

WHY:
Spring Security authorization checks need a principal and authorities.

### Attach request details

CODE:

```java
usernamePasswordAuthenticationToken
    .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
```

MEANING:
Adds request-specific details such as remote address and session id if
available.

WHY:
These details can be useful for auditing, logging, or later security features.

### Store authentication

CODE:

```java
SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
```

MEANING:
Stores the authenticated user for the current request.

WHY:
This is what makes `.authenticated()`, `.hasRole(...)`, and
`.hasAnyRole(...)` work for JWT requests.

### Existing or missing authentication path

CODE:

```java
} else {
    System.out.println("Cannot set the Security Context");
}
```

MEANING:
Runs when no username was extracted or the context already has authentication.

WHY:
In this project it only prints a message. It does not stop the request.
Authorization later decides whether the request is allowed.

### Invalid token parsing

CODE:

```java
} catch (IllegalArgumentException e) {
    System.out.println("Unable to get JWT Token");
}
```

MEANING:
Handles cases where token parsing receives an illegal value.

WHY:
The filter catches the exception so the chain can continue and Spring Security
can later reject the request if needed.

### Expired JWT handling

CODE:

```java
} catch (ExpiredJwtException ex) {
    String isRefreshToken = request.getHeader("isRefreshToken");
    String requestURL = request.getRequestURL().toString();
    if (isRefreshToken != null && isRefreshToken.equals("true") && requestURL.contains("refreshtoken")) {
        allowForRefreshToken(ex, request);
    } else
        request.setAttribute("exception", ex);
}
```

MEANING:
If the token is expired, the filter checks whether the request is trying to use
the refresh-token endpoint.

WHY:
This project allows an expired token to reach `/refreshtoken` only when:

- Header `isRefreshToken` is `true`
- URL contains `refreshtoken`

If those conditions are not met, the exception is stored on the request for
`JwtAuthenticationEntryPoint`.

### Bad credentials handling

CODE:

```java
} catch (BadCredentialsException ex) {
    request.setAttribute("exception", ex);
}
```

MEANING:
Stores invalid-token authentication errors on the request.

WHY:
`JwtAuthenticationEntryPoint` can read this attribute and return a JSON error.

### Continue the chain

CODE:

```java
chain.doFilter(request, response);
```

MEANING:
Passes the request to the next filter and eventually to the controller if
security allows it.

WHY:
Filters must continue the chain unless they fully handle the response.

### Refresh-token helper

CODE:

```java
UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
    new UsernamePasswordAuthenticationToken(null, null, null);
SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
request.setAttribute("claims", ex.getClaims());
```

MEANING:
For the refresh path, the filter creates an authentication object with null
principal, null credentials, and null authorities, then stores expired-token
claims on the request.

WHY:
`/refreshtoken` is protected by `.anyRequest().authenticated()`. This dummy
authentication allows the request to pass security so
`AuthenticationController.refreshtoken(...)` can create a new token from the
expired token's claims.

Project-specific warning:
This refresh-token behavior is not the standard production pattern. A production
system usually issues a separate refresh token, stores or rotates it, and
validates it independently.

## 12. Complete Architecture Diagram

### Login

```text
Client
  |
  | POST /authenticate
  v
AuthenticationController.createAuthenticationToken()
  |
  | UsernamePasswordAuthenticationToken(username, password)
  v
AuthenticationManager.authenticate(...)
  |
  v
Implicit DaoAuthenticationProvider
  |
  +--> CustomUserDetailsService.loadUserByUsername(username)
  |       |
  |       v
  |     UserRepository.findByUsername(username)
  |       |
  |       v
  |     Spring Security UserDetails
  |
  +--> BCryptPasswordEncoder password check
  |
  v
Authentication successful
  |
  v
AuthenticationController loads UserDetails again
  |
  v
JwtTokenUtil.generateToken(userDetails)
  |
  v
AuthenticationResponse(token)
  |
  v
Client
```

### Authenticated Request

```text
Client + Authorization: Bearer <JWT>
  |
  v
Spring Security filter chain
  |
  v
CustomJwtAuthenticationFilter.doFilterInternal(...)
  |
  v
JwtTokenUtil.getUsernameFromToken(jwtToken)
  |
  v
CustomUserDetailsService.loadUserByUsername(username)
  |
  v
JwtTokenUtil.validateToken(jwtToken, userDetails)
  |
  v
UsernamePasswordAuthenticationToken(userDetails, null, authorities)
  |
  v
SecurityContextHolder.getContext().setAuthentication(...)
  |
  v
SecurityConfiguration authorization rules
  |
  +--> /api/secure/info requires any authenticated user
  |
  +--> /admin-user requires ROLE_ADMIN
  |
  +--> /normal-user requires ROLE_ADMIN or ROLE_USER
  |
  +--> other endpoints require authenticated user
  |
  v
Controller method
```

### Refresh Token Flow in This Project

```text
Client + expired JWT + isRefreshToken: true
  |
  | GET /refreshtoken
  v
CustomJwtAuthenticationFilter
  |
  v
JwtTokenUtil tries to parse token
  |
  v
ExpiredJwtException
  |
  v
allowForRefreshToken(ex, request)
  |
  +--> set dummy Authentication in SecurityContextHolder
  |
  +--> request.setAttribute("claims", ex.getClaims())
  |
  v
AuthenticationController.refreshtoken(request)
  |
  v
JwtTokenUtil.doGenerateRefreshToken(claims, subject)
  |
  v
AuthenticationResponse(token)
```

## 13. Debugging Guide

### Debug login from Postman

Send:

```http
POST http://localhost:8080/authenticate
Content-Type: application/json
```

Body:

```json
{
  "username": "tuli",
  "password": "tuli"
}
```

Breakpoint 1:

Class:
`AuthenticationController`

Method:
`createAuthenticationToken(...)`

What to observe:
Check `authenticationRequest.getUsername()` and
`authenticationRequest.getPassword()`.

Breakpoint 2:

Class:
`AuthenticationController`

Line/method call:
`authenticationManager.authenticate(...)`

What to observe:
Step into this call to see Spring Security's authentication process.

Breakpoint 3:

Class:
`CustomUserDetailsService`

Method:
`loadUserByUsername(String username)`

What to observe:
Confirm the username passed by `DaoAuthenticationProvider`.

Breakpoint 4:

Class:
`CustomUserDetailsService`

Line/method call:
`userRepository.findByUsername(username)`

What to observe:
Check whether `UserEntity` is returned and whether `password` is a BCrypt hash.

Breakpoint 5:

Class:
`CustomUserDetailsService`

Line:
Role conversion loop.

What to observe:
Check `roleEntity.getRoleName()` values. They should look like `ROLE_USER` or
`ROLE_ADMIN`.

Breakpoint 6:

Class:
`CustomUserDetailsService`

Line:

```java
return new User(userModel.getUsername(), userModel.getPassword(), roleList);
```

What to observe:
Check the returned `UserDetails`: username, encoded password, and authorities.

Breakpoint 7:

Class:
`AuthenticationController`

Line after `authenticationManager.authenticate(...)`.

What to observe:
If execution reaches this line, username/password authentication succeeded.

Breakpoint 8:

Class:
`JwtTokenUtil`

Method:
`generateToken(UserDetails userDetails)`

What to observe:
Check `userDetails.getUsername()`.

Breakpoint 9:

Class:
`JwtTokenUtil`

Method:
`doGenerateToken(Map<String, Object> claims, String subject)`

What to observe:
Check subject, issued time, expiration time, and signing algorithm.

Breakpoint 10:

Class:
`AuthenticationController`

Line:

```java
return ResponseEntity.ok(new AuthenticationResponse(token));
```

What to observe:
Copy the returned JWT for the next request.

### Debug authenticated JWT request from Postman

Send:

```http
GET http://localhost:8080/normal-user
Authorization: Bearer <jwt-token>
```

Breakpoint 1:

Class:
`CustomJwtAuthenticationFilter`

Method:
`doFilterInternal(...)`

What to observe:
The filter runs before the controller.

Breakpoint 2:

Class:
`CustomJwtAuthenticationFilter`

Line:

```java
final String requestTokenHeader = request.getHeader("Authorization");
```

What to observe:
Confirm the header value starts with `Bearer `.

Breakpoint 3:

Class:
`CustomJwtAuthenticationFilter`

Line:

```java
jwtToken = requestTokenHeader.substring(7);
```

What to observe:
Confirm only the raw JWT remains.

Breakpoint 4:

Class:
`JwtTokenUtil`

Method:
`getUsernameFromToken(String token)`

What to observe:
Confirm the username extracted from the token subject.

Breakpoint 5:

Class:
`CustomJwtAuthenticationFilter`

Line:

```java
SecurityContextHolder.getContext().getAuthentication() == null
```

What to observe:
For a normal stateless request, this should usually be `null` before the filter
sets authentication.

Breakpoint 6:

Class:
`CustomUserDetailsService`

Method:
`loadUserByUsername(String username)`

What to observe:
Confirm the user is loaded again for this request.

Breakpoint 7:

Class:
`JwtTokenUtil`

Method:
`validateToken(String authToken, UserDetails userDetails)`

What to observe:
Check username comparison and expiration check.

Breakpoint 8:

Class:
`CustomJwtAuthenticationFilter`

Line:

```java
new UsernamePasswordAuthenticationToken(
    userDetails,
    null,
    userDetails.getAuthorities()
)
```

What to observe:
Check principal and authorities.

Breakpoint 9:

Class:
`CustomJwtAuthenticationFilter`

Line:

```java
SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
```

What to observe:
This is the exact line where the request becomes authenticated.

Breakpoint 10:

Class:
`ResourceController`

Method:
`normalUser()` or `adminUser()`

What to observe:
If the breakpoint is hit, authentication and authorization both passed.

### Debug failed authorization

Send a valid `ROLE_USER` JWT to:

```http
GET http://localhost:8080/admin-user
```

What to observe:

- `CustomJwtAuthenticationFilter` still authenticates the user.
- `SecurityContextHolder` contains `ROLE_USER`.
- The controller is not reached.
- Spring Security denies access because `/admin-user` requires `ROLE_ADMIN`.

## 14. Interview Explanation

In my project, Spring Security is configured in `SecurityConfiguration` with a
`SecurityFilterChain` bean. The application disables form login, HTTP Basic, and
CSRF, uses JWT for authenticated API requests, and enables OAuth2 login for the
Google browser redirect flow.

For login, the client calls `POST /authenticate` with username and password.
`AuthenticationController` creates a `UsernamePasswordAuthenticationToken` and
passes it to `AuthenticationManager`. The `AuthenticationManager` delegates to
the `DaoAuthenticationProvider`, which is configured with
`customUserDetailsService` and `passwordEncoder`.
That provider calls my `CustomUserDetailsService`, which loads the user from
`UserRepository` and returns Spring Security's built-in `User` object with the
encoded password and authorities. Spring Security then uses
`BCryptPasswordEncoder` to compare the raw password with the stored BCrypt hash.
If authentication succeeds, `AuthenticationController` calls `JwtTokenUtil` to
generate a JWT and returns it to the client.

For later requests, the client sends `Authorization: Bearer <JWT>`.
`CustomJwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`.
It extracts the JWT, reads the username from the token, loads the user again
with `CustomUserDetailsService`, validates the token through `JwtTokenUtil`, and
then creates a `UsernamePasswordAuthenticationToken` with the user's authorities.
It stores that authentication object in `SecurityContextHolder`. After that,
Spring Security's authorization rules decide whether the request can reach the
controller. `/admin-user` requires `ROLE_ADMIN`, `/normal-user` allows
`ROLE_ADMIN` or `ROLE_USER`, and other non-public endpoints require an
authenticated user.

## 15. Important Interview Questions

1. Why do we need `AuthenticationManager` in this project?

It is the Spring Security entry point for username/password authentication.
`AuthenticationController` delegates login validation to it instead of checking
passwords manually.

2. Which `AuthenticationProvider` is used?

The project uses Spring Security's `DaoAuthenticationProvider` implicitly. It is
created from `auth.userDetailsService(customUserDetailsService).passwordEncoder(passwordEncoder())`.

3. Why is there no explicit `DaoAuthenticationProvider` bean?

`AuthenticationManagerBuilder.userDetailsService(...).passwordEncoder(...)`
configures the provider internally.

4. Who calls `CustomUserDetailsService.loadUserByUsername()`?

During login, `DaoAuthenticationProvider` calls it. During JWT requests,
`CustomJwtAuthenticationFilter` calls it. The controller also calls it after
successful login before generating the JWT.

5. What does `CustomUserDetailsService` return?

It returns Spring Security's built-in `User` object containing username, encoded
password, and authorities.

6. How are roles converted to authorities?

Each `RoleEntity.roleName` is wrapped in `new SimpleGrantedAuthority(...)`.

7. Why does the database store `ROLE_USER` instead of `USER`?

Because `.hasRole("USER")` checks for authority `ROLE_USER`. The `ROLE_` prefix
is added by Spring Security's role convention.

8. What does `PasswordEncoder` do here?

`BCryptPasswordEncoder` encodes passwords during registration and verifies raw
login passwords against stored BCrypt hashes during authentication.

9. Where is the password encoded?

In `UserServiceImpl.saveUser(...)`:

```java
userModel.setPassword(bcryptEncoder.encode(userModel.getPassword()));
```

10. Where is the password compared?

Inside Spring Security's `DaoAuthenticationProvider`, using the configured
`BCryptPasswordEncoder`.

11. Why do we use `SecurityContextHolder`?

It stores the authenticated user for the current request so authorization rules
can check roles and authentication status.

12. Where is `SecurityContextHolder` populated?

In `CustomJwtAuthenticationFilter.doFilterInternal(...)` after JWT validation.

13. Why is the JWT filter executed before `UsernamePasswordAuthenticationFilter`?

The JWT must be validated and the security context must be populated before
Spring Security authorization decides whether the request can reach a controller.

14. What happens if JWT validation fails?

`JwtTokenUtil.validateToken(...)` throws `BadCredentialsException` for invalid
tokens or rethrows `ExpiredJwtException` for expired tokens. The filter stores
the exception on the request, and Spring Security eventually returns `401` for
protected endpoints.

15. What is the difference between authentication and authorization here?

Authentication verifies the user identity through username/password or JWT.
Authorization checks whether the authenticated user has the required role for
the endpoint.

16. Why do API requests still need a JWT?

The secured API flow is JWT-based. Google login needs a temporary session for
the browser redirect, but `/api/secure/**`, `/normal-user`, and `/admin-user`
should still be called with `Authorization: Bearer <application-jwt>`.

17. What happens to `SecurityContext` between requests?

For JWT API calls, the JWT filter rebuilds it for each request that sends a
valid token.

18. What causes `401 Unauthorized`?

Missing, invalid, or expired authentication for a protected endpoint.

19. What causes `403 Forbidden`?

The user is authenticated but lacks the required authority, such as `ROLE_USER`
calling `/admin-user`.

20. Does this project use OAuth2 or OpenID Connect?

No. It uses a custom JWT flow built with Spring Security and JJWT. OAuth2 and
OpenID Connect introduce additional concepts such as authorization servers,
clients, scopes, ID tokens, discovery metadata, and standard token endpoints.

## Potential Improvements / Security Issues

These are observations for future hardening.

1. The JWT secret is a learning-project value in `application.yml`.

The Google client secret can be read from local file config. The JWT secret is
still a learning-project value:

```yaml
jwt:
  secret: javainuse
```

Production systems should load secrets from environment variables, Vault,
Kubernetes secrets, or another secure secret manager.

2. `jjwt:0.9.1` is old.

Newer JJWT versions split API, implementation, and JSON serializer dependencies.

4. Refresh-token handling is not a standard production refresh-token design.

This project allows `/refreshtoken` when an expired token is sent with
`isRefreshToken: true`. A production system usually issues a separate refresh
token, stores or rotates it, and validates it independently.

5. The refresh flow creates an authentication object with null principal and
authorities.

```java
new UsernamePasswordAuthenticationToken(null, null, null)
```

This is enough to pass `.authenticated()` in this implementation path, but it is
not a strong authorization model.

6. `UserEntity.status` and `emailVerified` are not used by
`CustomUserDetailsService`.

Spring Security's `User` constructor used here does not consider this project's
status fields. Disabled, locked, expired, or unverified users are not blocked by
this code.

7. Registration returns `UserModel`, which still contains the encoded password.

`UserServiceImpl.saveUser(...)` mutates `userModel.password` to the BCrypt hash
and returns the model. API responses should generally avoid returning any
password field, even encoded.

8. Some authentication failures are wrapped in generic `Exception`.

`AuthenticationController` converts `DisabledException` and
`BadCredentialsException` into generic exceptions. A dedicated exception
handling strategy would make API responses cleaner.

9. Missing Bearer token logs a warning even for public endpoints.

`CustomJwtAuthenticationFilter` runs for public endpoints too, so `/authenticate`
and `/register` can produce "JWT Token does not begin with Bearer String" logs.

10. No custom `AccessDeniedHandler` is configured for `403`.

The project customizes `401` responses through `JwtAuthenticationEntryPoint`,
but does not customize the JSON body for forbidden responses.

11. The table name `"user"` can be awkward in SQL databases.

`user` is a reserved or special word in some databases. This project quotes it
in SQL scripts, but a less ambiguous table name such as `users` is often easier.

## Authentication-Only Endpoint

`GET /api/secure/info` demonstrates a protected endpoint that only requires a
valid JWT. It does not require `ROLE_USER`, `ROLE_ADMIN`, or any other specific
authority.

```text
Client
   |
   | Authorization: Bearer <JWT>
   v
JWT Filter
   |
   | validate token
   v
Authentication created
   |
   v
SecurityContextHolder
   |
   v
.authenticated()
   |
   v
AuthenticatedUserController
   |
   v
200 OK
```

`authenticated()` only asks: "Is this request from an authenticated user?" It
does not check whether the user is `USER` or `ADMIN`.

`hasRole("ADMIN")` checks authorization for a specific role. Therefore
`/api/secure/info` demonstrates authentication-only protection: a normal user
and an admin can both access it as long as the JWT is valid.

## Sample Requests

### Register User

```http
POST http://localhost:8080/register
Content-Type: application/json
```

```json
{
  "username": "tuli",
  "password": "tuli",
  "roleList": [
    {
      "roleName": "ROLE_USER"
    }
  ]
}
```

### Login

```http
POST http://localhost:8080/authenticate
Content-Type: application/json
```

```json
{
  "username": "tuli",
  "password": "tuli"
}
```

Example response:

```json
{
  "token": "<jwt-token>"
}
```

### Call User Endpoint

```bash
curl --location 'http://localhost:8080/normal-user' \
  --header 'Authorization: Bearer <jwt-token>'
```

### Call Admin Endpoint

```bash
curl --location 'http://localhost:8080/admin-user' \
  --header 'Authorization: Bearer <jwt-token>'
```

### Call Authentication-Only Endpoint

```bash
curl --location 'http://localhost:8080/api/secure/info' \
  --header 'Authorization: Bearer <jwt-token>'
```

### Refresh Token

This project's refresh flow expects an expired JWT and the `isRefreshToken`
header:

```bash
curl --location 'http://localhost:8080/refreshtoken' \
  --header 'isRefreshToken: true' \
  --header 'Authorization: Bearer <expired-jwt-token>'
```

## Important Configuration Values

From `application.yml`:

```yaml
jwt:
  secret: javainuse
  expirationDateInMs: 800000
  refreshExpirationDateInMs: 9000000

google:
  client-id: google-client-id-not-configured
  client-secret: google-client-secret-not-configured

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/SpringSecurity
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update
```

Token expiration values are milliseconds:

- Access token: `800000` ms, about 13 minutes and 20 seconds
- Refresh token: `9000000` ms, about 2 hours and 30 minutes

## Google Login With OAuth 2.0 and OIDC

This project now supports two login methods that converge into the same
application security model.

### Local Login

```text
Username + Password
        |
        v
AuthenticationController.createAuthenticationToken()
        |
        v
AuthenticationManager
        |
        v
DaoAuthenticationProvider
        |
        v
CustomUserDetailsService
        |
        v
Authenticated UserDetails
        |
        v
JwtTokenUtil.generateToken()
        |
        v
Application JWT
        |
        v
Client
```

### Google Login

```text
Browser
   |
   v
Spring Security OAuth2 Client
   |
   v
Google Authorization Server / OpenID Provider
   |
   | authentication + consent
   v
OIDC response
   |
   v
Spring Security
   |
   v
OidcUser
   |
   v
GoogleOAuth2AuthenticationSuccessHandler
   |
   v
GoogleOidcUserService.findOrCreateUser()
   |
   v
Local UserEntity with ROLE_USER
   |
   v
CustomUserDetailsService
   |
   v
JwtTokenUtil.generateToken()
   |
   v
Application JWT
   |
   v
Browser JSON response
```

### After Either Login Method

```text
Application JWT
      |
      v
CustomJwtAuthenticationFilter
      |
      v
JwtTokenUtil validates JWT
      |
      v
CustomUserDetailsService loads local user
      |
      v
Authentication
      |
      v
SecurityContextHolder
      |
      v
authenticated() / hasRole(...)
      |
      v
Controller
```

Google's token is not used to call `/api/secure/**`. Google login only proves
who the user is. After that, `GoogleOidcUserService` finds or creates a local
`UserEntity`, assigns `ROLE_USER` for newly created Google users, and the normal
`JwtTokenUtil` creates this application's JWT.

Google's `sub` claim is the stable provider-side user identifier. This project
stores it as `provider_id` with `provider=GOOGLE`. The application does not
blindly merge a username/password account with a Google account just because the
email strings match. Safe account linking should be an explicit user-confirmed
flow.

## OAuth2 vs OIDC

OAuth 2.0 is an authorization framework. It primarily answers:

```text
What is this client allowed to access?
```

OpenID Connect is an identity layer built on OAuth 2.0. It primarily answers:

```text
Who is this user?
```

For this project:

```text
Google Login
     =
OAuth 2.0 Authorization Code Flow
     +
OpenID Connect
     +
ID Token / UserInfo
     =
Verified Google identity

Verified Google identity
     -> Local application user
     -> Application role
     -> My application's JWT
```

## Token Comparison

| Token | Issuer | Consumer | Used for |
| --- | --- | --- | --- |
| Google Authorization Code | Google | Spring Security OAuth2 Client in this application | Short-lived browser callback value exchanged by the backend for Google tokens |
| Google Access Token | Google | This application, if it wants to call Google APIs | Access to Google resources. This project does not use it for Drive, Gmail, Photos, or Calendar |
| Google ID Token | Google | Spring Security OIDC support in this application | Verified Google identity: subject, email, name, email verification |
| Application JWT | This Spring Boot application | This Spring Boot application's `CustomJwtAuthenticationFilter` | Calling this application's secured APIs with local roles |

The application JWT is different from Google's ID token and access token. It is
issued by this application, uses this application's `jwt.secret`, contains the
local username as the JWT subject, and is validated by `JwtTokenUtil`.

## Google Cloud Setup

1. Open Google Cloud Console.
2. Create or select a project.
3. Configure the OAuth consent screen if Google asks for it.
4. Create credentials.
5. Choose OAuth Client ID.
6. Application type: Web application.
7. Add this authorized redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

This is the actual redirect URI for this project because there is no
`server.port` override and no servlet context path. Spring Boot uses port `8080`
by default, and Spring Security's Google registration id is `google`.

Provide the client values in either `application.yml`:

```yaml
google:
  client-id: your-google-client-id
  client-secret: your-google-client-secret
```

Or create a local `config.json` in the project root:

```json
{
  "google": {
    "client-id": "your-google-client-id",
    "client-secret": "your-google-client-secret"
  }
}
```

`config.json` is ignored by Git. Use `config.example.json` as the template.
When both files are present, `config.json` wins over `application.yml` for the
Google client id and secret.

Requested Google scopes:

```text
openid
profile
email
```

These scopes are for login and identity only.

## Browser and Postman Testing

### Browser

1. Start the Spring Boot application.
2. Open:

```text
http://localhost:8080/oauth2/authorization/google
```

3. The browser redirects to Google.
4. Login to Google.
5. Google redirects back to:

```text
http://localhost:8080/login/oauth2/code/google
```

6. Spring Security validates the OIDC response.
7. `GoogleOAuth2AuthenticationSuccessHandler` receives the `OidcUser`.
8. `GoogleOidcUserService` finds or creates the local user.
9. A new Google user receives only `ROLE_USER`.
10. `JwtTokenUtil` generates this application's JWT.
11. The browser displays a JSON response containing:

```json
{
  "tokenType": "Bearer",
  "token": "<application-jwt>"
}
```

This JSON token response is for DEVELOPMENT / LEARNING ONLY. It avoids putting
the JWT in a URL query parameter, but production applications should normally
use a secure HTTP-only cookie or a safer frontend/backend exchange mechanism.

### Postman

Import this collection into Postman:

```text
Spring-Security-Google-OIDC.postman_collection.json
```

The collection contains three folders:

- `Local Username Password Login`
- `Google OIDC Login`
- `Secured API With Application JWT`

Google login still starts in the browser. Postman does not complete this
project's Spring Security `oauth2Login` flow by itself because the flow uses
browser redirects, Google login UI, and a temporary server-side session.

Use the collection like this:

1. Run the Spring Boot application.
2. In Postman, confirm `baseUrl` is:

```text
http://localhost:8080
```

3. Open this URL in the browser:

```text
http://localhost:8080/oauth2/authorization/google
```

4. Complete Google login in the browser.
5. Copy the `token` value from the browser JSON response.
6. In Postman, set the collection variable:

```text
applicationJwt=<token copied from browser>
```

Alternatively, open the request:

```text
Google OIDC Login / 2. Paste Browser Application JWT Here
```

Paste the token into `tokenFromBrowser` in that request's Pre-request Script,
then send the request once. The script saves it into the `applicationJwt`
collection variable.

Then call the existing secured endpoint:

```http
GET http://localhost:8080/api/secure/info
Authorization: Bearer <application-jwt>
```

Expected result:

```text
HTTP 200
```

In the collection, this request is:

```text
Secured API With Application JWT / Authenticated Endpoint - /api/secure/info
```

Role behavior:

```text
LOCAL USER with ROLE_USER
    -> application JWT
    -> GET /api/secure/info
    -> 200

LOCAL ADMIN with ROLE_ADMIN
    -> application JWT
    -> GET /api/secure/info
    -> 200

GOOGLE USER
    -> Google OIDC login
    -> local ROLE_USER
    -> application JWT
    -> GET /api/secure/info
    -> 200

GOOGLE USER
    -> application JWT
    -> GET /admin-user
    -> 403 Forbidden
```

## IntelliJ Debugging Guide

Google login breakpoints:

| Class | Method | Inspect |
| --- | --- | --- |
| `OAuth2AuthorizationRequestRedirectFilter` | `doFilterInternal` | Browser request to `/oauth2/authorization/google` and redirect to Google |
| `OAuth2LoginAuthenticationFilter` | `attemptAuthentication` | Callback request to `/login/oauth2/code/google` |
| `OidcAuthorizationCodeAuthenticationProvider` | `authenticate` | Authorization code exchange and OIDC validation |
| `GoogleOAuth2AuthenticationSuccessHandler` | `onAuthenticationSuccess` | `OidcUser`, `sub`, email, name, `email_verified` |
| `GoogleOidcUserService` | `findOrCreateUser` | Lookup by `provider=GOOGLE` and Google `sub` |
| `GoogleOidcUserService` | `findOrCreateUser` | New user role list contains `ROLE_USER`, not `ROLE_ADMIN` |
| `JwtTokenUtil` | `generateToken` | Local username used as this application's JWT subject |

Application JWT request breakpoints:

| Class | Method | Inspect |
| --- | --- | --- |
| `CustomJwtAuthenticationFilter` | `doFilterInternal` | `Authorization: Bearer <application-jwt>` header |
| `JwtTokenUtil` | `getUsernameFromToken` | Local username extracted from JWT subject |
| `CustomUserDetailsService` | `loadUserByUsername` | Local user and authorities |
| `JwtTokenUtil` | `validateToken` | JWT signature, subject, and expiration |
| `CustomJwtAuthenticationFilter` | `doFilterInternal` | `SecurityContextHolder.getContext().setAuthentication(...)` |
| `AuthenticatedUserController` | `getSecureInfo` | Authenticated principal for `/api/secure/info` |

## Implementation Notes

- `spring-boot-starter-oauth2-client` was added using the existing Spring Boot
  dependency management.
- The project remains on Spring Boot `2.5.0`, so the configuration uses a
  component-style `SecurityFilterChain` compatible with this version instead of
  `WebSecurityConfigurerAdapter`.
- OAuth login needs a temporary session during the Google redirect flow, so
  `SecurityConfiguration` uses `SessionCreationPolicy.IF_REQUIRED`. Secured API
  calls still authenticate through the existing Bearer JWT filter.
- No duplicate JWT service, JWT filter, `UserDetailsService`, password encoder,
  security filter chain, or user model was added.
