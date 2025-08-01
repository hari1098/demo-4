export interface Bet {
  id?: number;
  sessionId: string;
  ticketId: number;
  amount: number;
  time?: string;
}

export interface BettingSession {
  sessionId: string;
  startTime: string;
  durationMinutes: number;
  active: boolean;
}

export interface BetRequest {
  ticketId: number;
  amount: number;
}