from datetime import datetime, timedelta, timezone

from fastapi import HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session, joinedload

from app.models import Book, Loan, User
from app.schemas import BookOut, LoanOut

ACTIVE = ("reserved", "issued")
LOAN_DAYS = 14


def taken_by_book(db: Session) -> dict[int, int]:
    rows = db.execute(
        select(Loan.book_id, func.count())
        .where(Loan.status.in_(ACTIVE))
        .group_by(Loan.book_id)
    ).all()
    return {book_id: count for book_id, count in rows}


def to_book(book: Book, taken: int) -> BookOut:
    return BookOut(
        id=book.id,
        title=book.title,
        author=book.author.name,
        year=book.year,
        publisher=book.publisher,
        total_copies=book.total_copies,
        available_copies=max(book.total_copies - taken, 0),
    )


def to_loan(loan: Loan) -> LoanOut:
    book = loan.book
    reader = loan.reader
    return LoanOut(
        id=loan.id,
        book_id=book.id,
        title=book.title,
        author=book.author.name,
        year=book.year,
        publisher=book.publisher,
        status=loan.status,
        reserved_at=loan.reserved_at,
        issued_at=loan.issued_at,
        due_date=loan.due_date,
        returned_at=loan.returned_at,
        reader_name=reader.full_name,
        reader_email=reader.email,
    )


def _loan_query():
    return select(Loan).options(
        joinedload(Loan.reader),
        joinedload(Loan.book).joinedload(Book.author),
    )


def list_books(db: Session, query: str | None) -> list[BookOut]:
    books = db.scalars(select(Book).options(joinedload(Book.author)).order_by(Book.title)).unique().all()
    taken = taken_by_book(db)
    needle = (query or "").strip().casefold()
    if needle:
        books = [
            book
            for book in books
            if needle in book.title.casefold() or needle in book.author.name.casefold()
        ]
    return [to_book(book, taken.get(book.id, 0)) for book in books]


def get_book(db: Session, book_id: int) -> BookOut:
    book = db.scalar(select(Book).options(joinedload(Book.author)).where(Book.id == book_id))
    if book is None:
        raise HTTPException(status_code=404, detail="Книга не найдена")
    taken = taken_by_book(db).get(book.id, 0)
    return to_book(book, taken)


def reserve_book(db: Session, reader: User, book_id: int) -> LoanOut:
    book = db.scalar(select(Book).options(joinedload(Book.author)).where(Book.id == book_id))
    if book is None:
        raise HTTPException(status_code=404, detail="Книга не найдена")
    existing = db.scalar(
        select(Loan).where(
            Loan.reader_id == reader.id,
            Loan.book_id == book_id,
            Loan.status.in_(ACTIVE),
        )
    )
    if existing is not None:
        raise HTTPException(status_code=409, detail="Эта книга уже забронирована вами")
    taken = taken_by_book(db).get(book.id, 0)
    if taken >= book.total_copies:
        raise HTTPException(status_code=409, detail="Нет свободных экземпляров")
    loan = Loan(
        reader=reader,
        book=book,
        status="reserved",
        reserved_at=datetime.now(timezone.utc),
    )
    db.add(loan)
    db.commit()
    return to_loan(_get_loan(db, loan.id))


def cancel_reservation(db: Session, reader: User, loan_id: int) -> None:
    loan = db.scalar(select(Loan).where(Loan.id == loan_id, Loan.reader_id == reader.id))
    if loan is None:
        raise HTTPException(status_code=404, detail="Бронь не найдена")
    if loan.status != "reserved":
        raise HTTPException(status_code=409, detail="Отменить можно только бронь")
    loan.status = "cancelled"
    db.commit()


def list_reader_loans(db: Session, reader: User, scope: str) -> list[LoanOut]:
    if scope == "active":
        statuses = ACTIVE
        order = Loan.reserved_at.desc()
    elif scope == "history":
        statuses = ("returned",)
        order = Loan.returned_at.desc()
    else:
        raise HTTPException(status_code=400, detail="Неизвестный раздел")
    loans = db.scalars(
        _loan_query().where(Loan.reader_id == reader.id, Loan.status.in_(statuses)).order_by(order)
    ).unique().all()
    return [to_loan(loan) for loan in loans]


def list_librarian_loans(db: Session) -> list[LoanOut]:
    loans = db.scalars(
        _loan_query()
        .where(Loan.status.in_(ACTIVE))
        .order_by(Loan.status.desc(), Loan.reserved_at.asc())
    ).unique().all()
    return [to_loan(loan) for loan in loans]


def _get_loan(db: Session, loan_id: int) -> Loan:
    loan = db.scalar(_loan_query().where(Loan.id == loan_id))
    if loan is None:
        raise HTTPException(status_code=404, detail="Запись не найдена")
    return loan


def issue_loan(db: Session, loan_id: int) -> LoanOut:
    loan = _get_loan(db, loan_id)
    if loan.status != "reserved":
        raise HTTPException(status_code=409, detail="Выдать можно только забронированную книгу")
    now = datetime.now(timezone.utc)
    loan.status = "issued"
    loan.issued_at = now
    loan.due_date = now.date() + timedelta(days=LOAN_DAYS)
    db.commit()
    return to_loan(_get_loan(db, loan.id))


def return_loan(db: Session, loan_id: int) -> LoanOut:
    loan = _get_loan(db, loan_id)
    if loan.status != "issued":
        raise HTTPException(status_code=409, detail="Принять можно только выданную книгу")
    loan.status = "returned"
    loan.returned_at = datetime.now(timezone.utc)
    db.commit()
    return to_loan(_get_loan(db, loan.id))
