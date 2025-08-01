import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { BettingService } from '../../services/betting.service';
import { BettingSession } from '../../models/bet.model';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-session-manager',
  templateUrl: './session-manager.component.html',
  styleUrls: ['./session-manager.component.scss']
})
export class SessionManagerComponent implements OnInit, OnDestroy {
  sessionForm: FormGroup;
  currentSession: BettingSession | null = null;
  loading = false;
  error: string | null = null;
  
  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private bettingService: BettingService
  ) {
    this.sessionForm = this.fb.group({
      sessionId: ['', [Validators.required, Validators.minLength(3)]],
      durationMinutes: [5, [Validators.required, Validators.min(1), Validators.max(60)]],
      taskIntervalSeconds: [15, [Validators.required, Validators.min(5), Validators.max(60)]]
    });
  }

  ngOnInit(): void {
    this.bettingService.currentSession$
      .pipe(takeUntil(this.destroy$))
      .subscribe(session => {
        this.currentSession = session;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  startSession(): void {
    if (this.sessionForm.valid) {
      this.loading = true;
      this.error = null;
      
      const { sessionId, durationMinutes, taskIntervalSeconds } = this.sessionForm.value;
      
      this.bettingService.startBettingSession(sessionId, durationMinutes, taskIntervalSeconds)
        .subscribe({
          next: (session) => {
            this.bettingService.setCurrentSession(session);
            this.bettingService.startPollingSession(sessionId);
            this.bettingService.startPollingBets(sessionId);
            this.loading = false;
          },
          error: (error) => {
            this.error = error;
            this.loading = false;
          }
        });
    }
  }

  stopSession(): void {
    if (this.currentSession) {
      this.loading = true;
      this.bettingService.stopBettingSession(this.currentSession.sessionId)
        .subscribe({
          next: () => {
            this.bettingService.setCurrentSession(null);
            this.loading = false;
          },
          error: (error) => {
            this.error = error;
            this.loading = false;
          }
        });
    }
  }

  getSessionTimeRemaining(): string {
    if (!this.currentSession) return '';
    
    const startTime = new Date(this.currentSession.startTime);
    const endTime = new Date(startTime.getTime() + this.currentSession.durationMinutes * 60000);
    const now = new Date();
    const remaining = endTime.getTime() - now.getTime();
    
    if (remaining <= 0) return 'Expired';
    
    const minutes = Math.floor(remaining / 60000);
    const seconds = Math.floor((remaining % 60000) / 1000);
    
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }
}