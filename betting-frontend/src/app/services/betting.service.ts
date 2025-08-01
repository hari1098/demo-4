import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject, interval } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { Bet, BettingSession, BetRequest } from '../models/bet.model';

@Injectable({
  providedIn: 'root'
})
export class BettingService {
  private readonly API_BASE_URL = 'http://localhost:8081/api/bets';
  
  private currentSessionSubject = new BehaviorSubject<BettingSession | null>(null);
  public currentSession$ = this.currentSessionSubject.asObservable();
  
  private betsSubject = new BehaviorSubject<Bet[]>([]);
  public bets$ = this.betsSubject.asObservable();

  constructor(private http: HttpClient) {}

  // Session Management
  startBettingSession(sessionId: string, durationMinutes: number = 5, taskIntervalSeconds: number = 15): Observable<BettingSession> {
    const params = {
      sessionId,
      durationMinutes: durationMinutes.toString(),
      taskIntervalSeconds: taskIntervalSeconds.toString()
    };
    
    return this.http.post<BettingSession>(`${this.API_BASE_URL}/session/start`, null, { params })
      .pipe(
        catchError(this.handleError)
      );
  }

  getSessionStatus(sessionId: string): Observable<BettingSession> {
    return this.http.get<BettingSession>(`${this.API_BASE_URL}/session/status/${sessionId}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  stopBettingSession(sessionId: string): Observable<void> {
    return this.http.post<void>(`${this.API_BASE_URL}/session/stop/${sessionId}`, null)
      .pipe(
        catchError(this.handleError)
      );
  }

  // Betting Operations
  placeBet(sessionId: string, betRequest: BetRequest): Observable<Bet> {
    return this.http.post<Bet>(`${this.API_BASE_URL}/place/${sessionId}`, betRequest)
      .pipe(
        catchError(this.handleError)
      );
  }

  getAllBetsForSession(sessionId: string): Observable<Bet[]> {
    return this.http.get<Bet[]>(`${this.API_BASE_URL}/all/${sessionId}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  getHighestBetForSession(sessionId: string): Observable<Bet> {
    return this.http.get<Bet>(`${this.API_BASE_URL}/highest/${sessionId}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  getLowestBetForSession(sessionId: string): Observable<Bet> {
    return this.http.get<Bet>(`${this.API_BASE_URL}/lowest/${sessionId}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  // Real-time updates
  startPollingBets(sessionId: string, intervalMs: number = 3000): void {
    interval(intervalMs).pipe(
      switchMap(() => this.getAllBetsForSession(sessionId))
    ).subscribe({
      next: (bets) => this.betsSubject.next(bets),
      error: (error) => console.error('Error polling bets:', error)
    });
  }

  startPollingSession(sessionId: string, intervalMs: number = 5000): void {
    interval(intervalMs).pipe(
      switchMap(() => this.getSessionStatus(sessionId))
    ).subscribe({
      next: (session) => this.currentSessionSubject.next(session),
      error: (error) => {
        console.error('Error polling session:', error);
        this.currentSessionSubject.next(null);
      }
    });
  }

  // State management
  setCurrentSession(session: BettingSession | null): void {
    this.currentSessionSubject.next(session);
  }

  getCurrentSession(): BettingSession | null {
    return this.currentSessionSubject.value;
  }

  private handleError(error: HttpErrorResponse) {
    let errorMessage = 'An unknown error occurred!';
    
    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Server-side error
      errorMessage = error.error?.message || `Error Code: ${error.status}\nMessage: ${error.message}`;
    }
    
    return throwError(() => errorMessage);
  }
}