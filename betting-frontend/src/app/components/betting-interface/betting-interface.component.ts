import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { BettingService } from '../../services/betting.service';
import { Bet, BettingSession } from '../../models/bet.model';
import { Subject, takeUntil, combineLatest } from 'rxjs';

@Component({
  selector: 'app-betting-interface',
  templateUrl: './betting-interface.component.html',
  styleUrls: ['./betting-interface.component.scss']
})
export class BettingInterfaceComponent implements OnInit, OnDestroy {
  betForm: FormGroup;
  currentSession: BettingSession | null = null;
  bets: Bet[] = [];
  highestBet: Bet | null = null;
  lowestBet: Bet | null = null;
  loading = false;
  error: string | null = null;
  
  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private bettingService: BettingService
  ) {
    this.betForm = this.fb.group({
      ticketId: ['', [Validators.required, Validators.min(1)]],
      amount: ['', [Validators.required, Validators.min(0.01)]]
    });
  }

  ngOnInit(): void {
    // Subscribe to current session and bets
    combineLatest([
      this.bettingService.currentSession$,
      this.bettingService.bets$
    ]).pipe(takeUntil(this.destroy$))
    .subscribe(([session, bets]) => {
      this.currentSession = session;
      this.bets = bets.sort((a, b) => new Date(b.time || '').getTime() - new Date(a.time || '').getTime());
      this.updateHighestAndLowestBets();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  placeBet(): void {
    if (this.betForm.valid && this.currentSession) {
      this.loading = true;
      this.error = null;
      
      const betRequest = {
        ticketId: parseInt(this.betForm.value.ticketId),
        amount: parseFloat(this.betForm.value.amount)
      };
      
      this.bettingService.placeBet(this.currentSession.sessionId, betRequest)
        .subscribe({
          next: (bet) => {
            // Refresh bets list
            this.refreshBets();
            this.betForm.reset();
            this.loading = false;
          },
          error: (error) => {
            this.error = error;
            this.loading = false;
          }
        });
    }
  }

  refreshBets(): void {
    if (this.currentSession) {
      this.bettingService.getAllBetsForSession(this.currentSession.sessionId)
        .subscribe({
          next: (bets) => {
            this.bets = bets.sort((a, b) => new Date(b.time || '').getTime() - new Date(a.time || '').getTime());
            this.updateHighestAndLowestBets();
          },
          error: (error) => {
            console.error('Error refreshing bets:', error);
          }
        });
    }
  }

  private updateHighestAndLowestBets(): void {
    if (this.currentSession) {
      // Get highest bet
      this.bettingService.getHighestBetForSession(this.currentSession.sessionId)
        .subscribe({
          next: (bet) => this.highestBet = bet,
          error: () => this.highestBet = null
        });
      
      // Get lowest bet
      this.bettingService.getLowestBetForSession(this.currentSession.sessionId)
        .subscribe({
          next: (bet) => this.lowestBet = bet,
          error: () => this.lowestBet = null
        });
    }
  }

  trackByBetId(index: number, bet: Bet): number | undefined {
    return bet.id;
  }

  getMyBets(): Bet[] {
    const ticketId = parseInt(this.betForm.value.ticketId);
    if (!ticketId) return [];
    
    return this.bets.filter(bet => bet.ticketId === ticketId);
  }

  getMyHighestBet(): number {
    const myBets = this.getMyBets();
    if (myBets.length === 0) return 0;
    
    return Math.max(...myBets.map(bet => bet.amount));
  }

  getTotalBetsCount(): number {
    return this.bets.length;
  }

  getTotalBettingAmount(): number {
    return this.bets.reduce((total, bet) => total + bet.amount, 0);
  }

  getUniqueBettorsCount(): number {
    const uniqueTicketIds = new Set(this.bets.map(bet => bet.ticketId));
    return uniqueTicketIds.size;
  }
}