from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Author, Book, User
from app.security import hash_password

BOOKS = [
    ("Мастер и Маргарита", "Михаил Булгаков", 1967, "Художественная литература", 3),
    ("Собачье сердце", "Михаил Булгаков", 1925, "Азбука", 2),
    ("Преступление и наказание", "Фёдор Достоевский", 1866, "Эксмо", 2),
    ("Война и мир", "Лев Толстой", 1869, "АСТ", 2),
    ("Евгений Онегин", "Александр Пушкин", 1833, "Наука", 1),
    ("Сто лет одиночества", "Габриэль Гарсиа Маркес", 1967, "Азбука", 0),
    ("1984", "Джордж Оруэлл", 1949, "АСТ", 2),
    ("Маленький принц", "Антуан де Сент-Экзюпери", 1943, "Эксмо", 4),
]


def seed(db: Session) -> None:
    existing = db.scalar(select(func.count()).select_from(User))
    if existing:
        return

    db.add(
        User(
            full_name="Библиотекарь",
            email="librarian@library.local",
            password_hash=hash_password("librarian"),
            role="librarian",
        )
    )
    authors: dict[str, Author] = {}
    for title, author_name, year, publisher, copies in BOOKS:
        author = authors.get(author_name)
        if author is None:
            author = Author(name=author_name)
            authors[author_name] = author
            db.add(author)
        db.add(
            Book(
                title=title,
                author=author,
                year=year,
                publisher=publisher,
                total_copies=copies,
            )
        )
    db.commit()
