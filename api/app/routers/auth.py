from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import User
from app.schemas import LoginIn, RegisterIn, TokenOut, UserOut
from app.security import create_token, get_current_user, hash_password, normalize_email, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


def user_out(user: User) -> UserOut:
    return UserOut(id=user.id, full_name=user.full_name, email=user.email, role=user.role)


def token_out(user: User) -> TokenOut:
    return TokenOut(access_token=create_token(user), user=user_out(user))


@router.post("/register", response_model=TokenOut, status_code=201)
def register(body: RegisterIn, db: Session = Depends(get_db)) -> TokenOut:
    email = normalize_email(body.email)
    name = body.full_name.strip()
    if len(name) < 2:
        raise HTTPException(status_code=422, detail="Укажите имя")
    existing = db.scalar(select(User).where(User.email == email))
    if existing is not None:
        raise HTTPException(status_code=409, detail="Пользователь с таким email уже зарегистрирован")
    user = User(
        full_name=name,
        email=email,
        password_hash=hash_password(body.password),
        role="reader",
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return token_out(user)


@router.post("/login", response_model=TokenOut)
def login(body: LoginIn, db: Session = Depends(get_db)) -> TokenOut:
    email = body.email.strip().lower()
    user = db.scalar(select(User).where(User.email == email))
    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Неверный email или пароль")
    return token_out(user)


@router.get("/me", response_model=UserOut)
def me(user: User = Depends(get_current_user)) -> UserOut:
    return user_out(user)
