from datetime import date, datetime

from pydantic import BaseModel, Field


class RegisterIn(BaseModel):
    full_name: str = Field(min_length=2, max_length=200)
    email: str = Field(min_length=3, max_length=200)
    password: str = Field(min_length=6, max_length=100)


class LoginIn(BaseModel):
    email: str
    password: str


class UserOut(BaseModel):
    id: int
    full_name: str
    email: str
    role: str


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


class BookOut(BaseModel):
    id: int
    title: str
    author: str
    year: int
    publisher: str
    total_copies: int
    available_copies: int


class LoanOut(BaseModel):
    id: int
    book_id: int
    title: str
    author: str
    year: int
    publisher: str
    status: str
    reserved_at: datetime
    issued_at: datetime | None
    due_date: date | None
    returned_at: datetime | None
    reader_name: str
    reader_email: str
