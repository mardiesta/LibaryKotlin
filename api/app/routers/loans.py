from fastapi import APIRouter, Depends, Response
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import User
from app.schemas import LoanOut
from app.security import get_current_user
from app.service import cancel_reservation, list_reader_loans, reserve_book

router = APIRouter(tags=["loans"])


@router.post("/books/{book_id}/reservations", response_model=LoanOut, status_code=201)
def reserve(
    book_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> LoanOut:
    return reserve_book(db, user, book_id)


@router.delete("/loans/{loan_id}", status_code=204)
def cancel(
    loan_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Response:
    cancel_reservation(db, user, loan_id)
    return Response(status_code=204)


@router.get("/me/loans", response_model=list[LoanOut])
def my_loans(
    scope: str = "active",
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> list[LoanOut]:
    return list_reader_loans(db, user, scope)
