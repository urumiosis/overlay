-- Paste into Supabase Dashboard > SQL Editor > New query > Run.
-- Use a NEW project or run only once. Do not expose rows to unauthenticated visitors.
create table public.monika_commands (
    user_id uuid primary key references auth.users(id) on delete cascade,
    command_id uuid not null default gen_random_uuid(),
    action text not null check (action in ('SHOW', 'HIDE')),
    updated_at timestamptz not null default now()
);

alter table public.monika_commands enable row level security;
revoke all on table public.monika_commands from anon, authenticated;
grant select, insert, update on table public.monika_commands to authenticated;

create policy "Read own commands"
on public.monika_commands for select to authenticated
using ((select auth.uid()) = user_id);

create policy "Insert own commands"
on public.monika_commands for insert to authenticated
with check ((select auth.uid()) = user_id);

create policy "Update own commands"
on public.monika_commands for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

-- No Realtime publication is required for this version: Receiver polls HTTPS every 3 seconds.
