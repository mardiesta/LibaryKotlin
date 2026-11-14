from datetime import datetime, timedelta, timezone


def register(client, email="anna@example.com"):
    response = client.post(
        "/auth/register",
        json={"full_name": "Анна Иванова", "email": email, "password": "secret1"},
    )
    assert response.status_code == 201
    body = response.json()
    return {"Authorization": f"Bearer {body['access_token']}"}, body["user"]


def librarian(client):
    response = client.post(
        "/auth/login",
        json={"email": "librarian@library.local", "password": "librarian"},
    )
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def book_by_title(client, headers, title):
    books = client.get("/books", headers=headers).json()
    return next(book for book in books if book["title"] == title)


def test_full_loan_cycle(client):
    headers, _user = register(client)
    unavailable = book_by_title(client, headers, "Сто лет одиночества")
    assert unavailable["available_copies"] == 0
    denied = client.post(f"/books/{unavailable['id']}/reservations", headers=headers)
    assert denied.status_code == 409

    book = book_by_title(client, headers, "Мастер и Маргарита")
    reserved = client.post(f"/books/{book['id']}/reservations", headers=headers)
    assert reserved.status_code == 201
    loan_id = reserved.json()["id"]
    assert reserved.json()["status"] == "reserved"

    again = client.post(f"/books/{book['id']}/reservations", headers=headers)
    assert again.status_code == 409

    after_reserve = client.get(f"/books/{book['id']}", headers=headers).json()
    assert after_reserve["available_copies"] == book["available_copies"] - 1

    staff = librarian(client)
    queue = client.get("/librarian/loans", headers=staff).json()
    assert any(item["id"] == loan_id and item["reader_email"] == "anna@example.com" for item in queue)

    issued = client.post(f"/librarian/loans/{loan_id}/issue", headers=staff)
    assert issued.status_code == 200
    due = (datetime.now(timezone.utc).date() + timedelta(days=14)).isoformat()
    assert issued.json()["status"] == "issued"
    assert issued.json()["due_date"] == due

    active = client.get("/me/loans", params={"scope": "active"}, headers=headers).json()
    assert len(active) == 1
    assert active[0]["due_date"] == due
    assert client.delete(f"/loans/{loan_id}", headers=headers).status_code == 409

    returned = client.post(f"/librarian/loans/{loan_id}/return", headers=staff)
    assert returned.status_code == 200
    assert returned.json()["status"] == "returned"

    assert client.get("/me/loans", params={"scope": "active"}, headers=headers).json() == []
    history = client.get("/me/loans", params={"scope": "history"}, headers=headers).json()
    assert history[0]["id"] == loan_id
    restored = client.get(f"/books/{book['id']}", headers=headers).json()
    assert restored["available_copies"] == book["available_copies"]
    assert client.get("/librarian/loans", headers=staff).json() == []


def test_reader_can_cancel_reservation_and_search(client):
    headers, user = register(client, "boris@example.com")
    assert user["role"] == "reader"
    book = book_by_title(client, headers, "Евгений Онегин")
    loan_id = client.post(f"/books/{book['id']}/reservations", headers=headers).json()["id"]
    assert client.delete(f"/loans/{loan_id}", headers=headers).status_code == 204
    assert client.get("/me/loans", params={"scope": "active"}, headers=headers).json() == []
    assert client.get(f"/books/{book['id']}", headers=headers).json()["available_copies"] == book["available_copies"]

    found = client.get("/books", params={"q": "булгаков"}, headers=headers).json()
    assert {item["title"] for item in found} >= {"Мастер и Маргарита", "Собачье сердце"}
    assert client.get("/books", params={"q": "нет такой книги"}, headers=headers).json() == []


def test_access_rules(client):
    headers, _user = register(client, "vera@example.com")
    assert client.get("/books").status_code == 401
    assert client.get("/librarian/loans", headers=headers).status_code == 403
    assert client.post("/auth/login", json={"email": "vera@example.com", "password": "wrong"}).status_code == 401
    assert client.get("/auth/me", headers=headers).json()["email"] == "vera@example.com"
    duplicate = client.post(
        "/auth/register",
        json={"full_name": "Вера", "email": "vera@example.com", "password": "secret1"},
    )
    assert duplicate.status_code == 409
