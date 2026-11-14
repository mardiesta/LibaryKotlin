from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import User
from app.schemas import LoanOut
from app.security import require_librarian
from app.service import issue_loan, list_librarian_loans, return_loan

router = APIRouter(prefix="/librarian", tags=["librarian"])


@router.get("/loans", response_model=list[LoanOut])
def loans(db: Session = Depends(get_db), _: User = Depends(require_librarian)) -> list[LoanOut]:
    return list_librarian_loans(db)


@router.post("/loans/{loan_id}/issue", response_model=LoanOut)
def issue(loan_id: int, db: Session = Depends(get_db), _: User = Depends(require_librarian)) -> LoanOut:
    return issue_loan(db, loan_id)


@router.post("/loans/{loan_id}/return", response_model=LoanOut)
def accept_return(
    loan_id: int,
    db: Session = Depends(get_db),
    _: User = Depends(require_librarian),
) -> LoanOut:
    return return_loan(db, loan_id)
