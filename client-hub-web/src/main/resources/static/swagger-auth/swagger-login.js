(function () {
  "use strict";
  var activeTenantId = "default";
  var statusElement = document.getElementById("swagger-login-status");
  var form = document.getElementById("swagger-login-form");
  var button = document.getElementById("swagger-login-button");
  var ui = SwaggerUIBundle({
    url: "/v3/api-docs",
    dom_id: "#swagger-ui",
    deepLinking: true,
    displayRequestDuration: true,
    persistAuthorization: false,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: "StandaloneLayout",
    requestInterceptor: function (request) {
      if (activeTenantId && request.url.indexOf("/api/") !== -1) {
        request.headers = request.headers || {};
        request.headers["X-Tenant-ID"] = activeTenantId;
      }
      return request;
    }
  });
  window.ui = ui;

  function showStatus(message, state) {
    statusElement.textContent = message;
    statusElement.dataset.state = state || "";
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    var tenant = document.getElementById("swagger-tenant").value.trim().toLowerCase();
    var email = document.getElementById("swagger-email").value.trim();
    var passwordInput = document.getElementById("swagger-password");
    button.disabled = true;
    showStatus("Signing in...", "");
    fetch("/api/auth/login", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json", "X-Tenant-ID": tenant },
      body: JSON.stringify({ email: email, password: passwordInput.value })
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (!response.ok) throw new Error(body.message || "Sign-in failed. Check the workspace and credentials.");
        return body;
      });
    }).then(function (auth) {
      activeTenantId = auth.tenant_id || tenant;
      ui.authActions.authorize({
        bearerAuth: {
          name: "bearerAuth",
          schema: { type: "http", scheme: "bearer", bearerFormat: "JWT", in: "header" },
          value: auth.access_token
        }
      });
      passwordInput.value = "";
      showStatus("Authorized as " + auth.email + " in workspace " + activeTenantId + ".", "success");
    }).catch(function (error) {
      showStatus(error.message, "error");
    }).finally(function () {
      button.disabled = false;
    });
  });
}());
