# spring-security

Spring Boot JWT authentication example using Spring Security, a custom
`UserDetailsService`, BCrypt password hashing, and stateless request
authorization.

## Security Flow

### 1. Request enters the Spring Security filter chain

Every HTTP request reaches Spring Security before it reaches a controller.
The security rules are configured in `SecurityConfiguration`.

The application disables:

- Form login
- HTTP Basic authentication
- CSRF protection
- Server-side sessions

The app uses stateless JWT authentication:

```java
sessionManagement()
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

### 2. Public endpoints are allowed without JWT

These endpoints are available without authentication:

- `POST /authenticate`
- `POST /register`

They are configured with:

```java
.antMatchers("/authenticate", "/register").permitAll()
```

### 3. Login request: `/authenticate`

For a login request, `AuthenticationController` receives username and
password:

```http
POST /authenticate
```

The controller creates a `UsernamePasswordAuthenticationToken`:

```java
new UsernamePasswordAuthenticationToken(username, password)
```

Then it calls:

```java
authenticationManager.authenticate(...)
```

Spring Security uses the configured `AuthenticationManager`. This project
does not declare an explicit `AuthenticationProvider` bean. Instead, Spring
creates a `DaoAuthenticationProvider` from this configuration:

```java
auth.userDetailsService(customUserDetailsService)
    .passwordEncoder(passwordEncoder());
```

Authentication flow:

1. `DaoAuthenticationProvider` calls `CustomUserDetailsService`.
2. `CustomUserDetailsService` loads the user from `UserRepository`.
3. Spring compares the submitted password with the stored BCrypt password.
4. If authentication succeeds, the controller generates a JWT.
5. The response returns the JWT to the client.

### 4. Protected request with JWT

For protected endpoints, the client must send the JWT in the `Authorization`
header:

```http
Authorization: Bearer <jwt-token>
```

Before the request reaches a controller, `CustomJwtAuthenticationFilter` runs
before `UsernamePasswordAuthenticationFilter`:

```java
.addFilterBefore(customJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

The filter:

1. Reads the `Authorization` header.
2. Checks that it starts with `Bearer `.
3. Extracts the JWT.
4. Reads the username from the token.
5. Loads the user with `CustomUserDetailsService`.
6. Validates the token with `JwtTokenUtil`.
7. Creates a `UsernamePasswordAuthenticationToken`.
8. Stores it in `SecurityContextHolder`.

After this, Spring Security considers the request authenticated.

### 5. Authorization rules are checked

After authentication, Spring checks whether the authenticated user has the
required role for the requested endpoint:

```java
.antMatchers("/admin-user").hasRole("ADMIN")
.antMatchers("/normal-user").hasAnyRole("ADMIN", "USER")
.anyRequest().authenticated()
```

Role behavior:

- `/admin-user` requires `ROLE_ADMIN`.
- `/normal-user` allows `ROLE_ADMIN` or `ROLE_USER`.
- Any other endpoint requires a valid authenticated user.

If the user is allowed, the request reaches the controller method.

### 6. Controller receives the authenticated request

Example protected controller endpoints:

```java
@RequestMapping({"/admin-user"})
public String adminUser() {
    return "Hello Admin";
}

@RequestMapping({"/normal-user"})
public String normalUser() {
    return "Hello User";
}
```

At this point, the JWT has already been validated and the authenticated user
is available through Spring Security's `SecurityContextHolder`.

### 7. Invalid or missing JWT

If the JWT is missing, invalid, expired, or the user does not have permission,
Spring Security blocks the request before the controller runs.

`JwtAuthenticationEntryPoint` returns a `401 Unauthorized` JSON response for
authentication failures.

## Create User Payload

Simple JSON payload to create a user:

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

## Refresh Token Curl

```bash
curl --location 'http://localhost:8080/refreshtoken' \
  --header 'isRefreshToken: true' \
  --header 'Authorization: Bearer <expired-jwt-token>'
```
