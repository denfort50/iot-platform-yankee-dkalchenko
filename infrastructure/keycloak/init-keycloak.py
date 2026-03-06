#!/usr/bin/env python3
"""
Создаёт тестового пользователя в Keycloak realm master и включает Direct access grants
для клиента admin-cli. Запускается один раз после старта Keycloak (keycloak-init сервис).
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

KEYCLOAK_URL = os.environ.get("KEYCLOAK_URL", "http://keycloak:8080")
# В Keycloak 26 health отдаётся на порту 9000 (management), не на 8080
KEYCLOAK_HEALTH_URL = os.environ.get("KEYCLOAK_HEALTH_URL", "http://keycloak:9000")
KEYCLOAK_ADMIN = os.environ.get("KEYCLOAK_ADMIN", "admin")
KEYCLOAK_ADMIN_PASSWORD = os.environ.get("KEYCLOAK_ADMIN_PASSWORD", "admin")
TEST_USERNAME = os.environ.get("KEYCLOAK_TEST_USERNAME", "device-api-user")
TEST_PASSWORD = os.environ.get("KEYCLOAK_TEST_PASSWORD", "device-api-password")


def request(method, path, data=None, token=None, expected_status=None):
    url = f"{KEYCLOAK_URL}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            body = r.read().decode() if r.length else ""
            return r.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode() if e.fp else ""


def wait_for_keycloak():
    for i in range(60):
        try:
            req = urllib.request.Request(f"{KEYCLOAK_HEALTH_URL}/health/ready", method="GET")
            with urllib.request.urlopen(req, timeout=5) as r:
                if r.status == 200:
                    return
        except Exception:
            pass
        time.sleep(2)
    sys.exit("Keycloak did not become ready in time")


def get_admin_token():
    path = "/realms/master/protocol/openid-connect/token"
    body = (
        f"grant_type=password&client_id=admin-cli&username={KEYCLOAK_ADMIN}"
        f"&password={KEYCLOAK_ADMIN_PASSWORD}"
    ).encode()
    req = urllib.request.Request(
        f"{KEYCLOAK_URL}{path}",
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as r:
        data = json.loads(r.read().decode())
        return data["access_token"]


def create_user(token):
    path = "/admin/realms/master/users"
    payload = {
        "username": TEST_USERNAME,
        "enabled": True,
        "credentials": [
            {"type": "password", "value": TEST_PASSWORD, "temporary": False}
        ],
    }
    status, _ = request("POST", path, data=json.dumps(payload).encode(), token=token)
    if status in (201, 204):
        print(f"User '{TEST_USERNAME}' created.")
    elif status == 409:
        print(f"User '{TEST_USERNAME}' already exists, skipping.")
    else:
        print(f"Create user returned {status}", file=sys.stderr)


def enable_direct_access_grants(token):
    status, body = request("GET", "/admin/realms/master/clients?clientId=admin-cli", token=token)
    if status != 200:
        print("Could not get admin-cli client.", file=sys.stderr)
        return
    clients = json.loads(body)
    if not clients:
        print("admin-cli client not found.", file=sys.stderr)
        return
    client = clients[0]
    if client.get("directAccessGrantsEnabled"):
        print("admin-cli already has Direct access grants enabled.")
        return
    client_id = client["id"]
    client["directAccessGrantsEnabled"] = True
    status, _ = request(
        "PUT",
        f"/admin/realms/master/clients/{client_id}",
        data=json.dumps(client).encode(),
        token=token,
    )
    if status in (200, 204):
        print("admin-cli: Direct access grants enabled.")
    else:
        print(f"Enable direct access returned {status}", file=sys.stderr)


def main():
    print("Waiting for Keycloak...")
    wait_for_keycloak()
    print("Getting admin token...")
    token = get_admin_token()
    create_user(token)
    enable_direct_access_grants(token)
    print("Keycloak init done.")


if __name__ == "__main__":
    main()
